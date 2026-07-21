package com.loudnoisedetectionapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.RingtoneManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.MicrophoneInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class AudioMonitorService : Service(), SensorEventListener, AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "audio_monitor_service"
        private const val ALERT_CHANNEL_ID = "audio_monitor_alerts"

        private const val FOREGROUND_ID = 1
        private const val ALERT_ID = 2

        private const val ACTION_STOP_SERVICE = "STOP_AUDIO_MONITOR_SERVICE"
    }

    private var settingsManager: SettingsManager? = null
    private var exposureManager: ExposureManager? = null
    private var historyManager: HistoryManager? = null
    private var lastNotificationTime = 0L
    private var noiseStartTime = 0L
    private val NOTIFICATION_COOLDOWN = 5000L // 5 seconds between alerts
    private var lastCallbackTime = 0L

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioManager: AudioManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    @Volatile
    private var currentMovementIntensity = 0f

    // Grouping logic state
    private var isLoudEpisodeActive = false
    private var episodeMaxDb = 0f
    private var episodeF1Sum = 0f
    private var episodeF2Sum = 0f
    private var episodeSampleCount = 0
    private var episodeStartTime = 0L
    private var lastLoudTime = 0L
    private val EPISODE_TIMEOUT_MS = 8000L // End episode after 8s of quiet
    
    private var episodeLat: Double? = null
    private var episodeLon: Double? = null
    private var wasSelfNoiseInEpisode = false

    private var lastDoseSampleTime = 0L
    private val DOSE_SAMPLE_INTERVAL_MS = 60000L // 1 minute
    
    private var lastWidgetUpdateTime = 0L
    private val WIDGET_UPDATE_INTERVAL_MS = 2000L

    private var echoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: android.media.audiofx.AutomaticGainControl? = null
    private var isSelfNoiseActive = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            // Check if any playback is active through speakers
            isSelfNoiseActive = configs.any { config ->
                config.audioAttributes.usage == android.media.AudioAttributes.USAGE_MEDIA
            }
            AudioBridge.isSelfNoiseActive = isSelfNoiseActive
        }
    }

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val WATCHDOG_INTERVAL_MS = 30000L // Check every 30s
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (lastCallbackTime != 0L && (now - lastCallbackTime > WATCHDOG_INTERVAL_MS)) {
                Log.w("AudioMonitorService", "Watchdog detected stalled audio monitoring. Restarting...")
                restartMonitoring()
            }
            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Restart if settings changed
        if (intent?.getBooleanExtra("restart", false) == true) {
            restartMonitoring()
        }
        // START_STICKY tells the system to recreate the service if it's killed
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // This is called when the user swipes the app away from recents.
        // We do NOTHING here because we want the service to keep running.
        // On some devices, adding a small log or a slight delay can help 
        // ensure the system doesn't kill the service along with the activity.
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Audio Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification"
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Noise Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for loud noise and daily exposure"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                enableLights(true)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Start foreground ASAP to prevent system crashes on startup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_ID,
                buildForegroundNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                FOREGROUND_ID,
                buildForegroundNotification()
            )
        }

        settingsManager = SettingsManager(this)
        exposureManager = ExposureManager(this)
        historyManager = HistoryManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        audioManager.registerAudioPlaybackCallback(playbackCallback, Handler(Looper.getMainLooper()))

        // Initialize wake lock to prevent CPU sleep during long-term monitoring
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoiseMonitor:WakeLock")
        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours max safety timeout

        // Initialize accelerometer for handling noise compensation
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        logMicrophoneSpecs()
        startMonitoring()
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    private fun restartMonitoring() {
        AudioBridge.stop()
        startMonitoring()
    }

    private val serviceCallback = object : SpectrumCallback {
        override fun onSpectrum(
            spectrum: FloatArray,
            db: Float,
            dbRaw: Float,
            dbAlert: Float,
            dbL: Float,
            dbR: Float,
            balance: Float,
            rejectionActive: Boolean,
            channelCount: Int
        ) {
            val now = System.currentTimeMillis()

        // Movement-based noise compensation is now handled in the native engine.
        // We directly use the 'db' value received here.

        // Fix: Use exact buffer duration for dose calculation to avoid timing drift
        val deltaTimeSeconds = AudioBridge.BUFFER_DURATION.toFloat()
        exposureManager?.addExposure(db, deltaTimeSeconds)
        
        if (exposureManager?.shouldNotify() == true) {
            sendExposureNotification(exposureManager?.getCurrentDose() ?: 0f)
            exposureManager?.markNotified()
        }
        lastCallbackTime = now

        // Widget Update
        if (now - lastWidgetUpdateTime > WIDGET_UPDATE_INTERVAL_MS) {
            val dose = exposureManager?.getCurrentDose() ?: 0f
            updateWidget(db, dose)
            lastWidgetUpdateTime = now
        }

        // Dose sampling
        if (now - lastDoseSampleTime > DOSE_SAMPLE_INTERVAL_MS) {
            historyManager?.addDoseSample(exposureManager?.getCurrentDose() ?: 0f)
            lastDoseSampleTime = now
        }

        val threshold = settingsManager?.thresholdDb ?: 82f

        // Fix: Use dbAlert (responsive peak) for threshold checks
        if (dbAlert > threshold) {
            lastLoudTime = now
            if (!isLoudEpisodeActive) {
                isLoudEpisodeActive = true
                episodeStartTime = now
                episodeMaxDb = dbAlert
                val freqs = findTopFrequencies(spectrum)
                episodeF1Sum = freqs.first
                episodeF2Sum = freqs.second
                episodeSampleCount = 1
                wasSelfNoiseInEpisode = isSelfNoiseActive

                AudioBridge.startRecording()
                
                // Fetch location for this episode
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                episodeLat = location.latitude
                                episodeLon = location.longitude
                            }
                        }
                } catch (e: SecurityException) {
                    Log.w("AudioMonitorService", "Location permission not granted for tagging")
                }
            } else {
                episodeMaxDb = maxOf(episodeMaxDb, dbAlert)
                val freqs = findTopFrequencies(spectrum)
                episodeF1Sum += freqs.first
                episodeF2Sum += freqs.second
                episodeSampleCount++
                if (isSelfNoiseActive) wasSelfNoiseInEpisode = true
            }
        } else {
            if (isLoudEpisodeActive && (now - lastLoudTime > EPISODE_TIMEOUT_MS)) {
                // End Episode
                val duration = (lastLoudTime - episodeStartTime) / 1000f
                if (duration > (settingsManager?.durationSeconds ?: 1f)) {
                    val snippetFileName = "noise_${System.currentTimeMillis()}.wav"
                    val snippetFile = java.io.File(cacheDir, snippetFileName)
                    val saved = AudioBridge.saveSnippet(snippetFile.absolutePath)
                    val snippetPath = if (saved) snippetFile.absolutePath else null

                    historyManager?.addEvent(
                        episodeMaxDb,
                        episodeF1Sum / episodeSampleCount,
                        episodeF2Sum / episodeSampleCount,
                        duration,
                        episodeLat,
                        episodeLon,
                        snippetPath,
                        wasSelfNoiseInEpisode
                    )

                    if (now - lastNotificationTime > NOTIFICATION_COOLDOWN) {
                        sendLoudNoiseNotification(episodeMaxDb.toDouble(), duration)
                        lastNotificationTime = now
                    }
                }
                isLoudEpisodeActive = false
                episodeLat = null
                episodeLon = null
                wasSelfNoiseInEpisode = false
            }
        }
        }
    }

    private fun findTopFrequencies(spectrum: FloatArray): Pair<Float, Float> {
        var max1 = -1f
        var idx1 = -1
        var max2 = -1f
        var idx2 = -1

        // Constant from AudioEngine
        val binWidth = 44100f / 8192f

        // Start from index 2 (~10Hz) to skip DC offset and extreme low rumble
        for (i in 2 until spectrum.size) {
            val value = spectrum[i]
            if (value > max1) {
                max2 = max1
                idx2 = idx1
                max1 = value
                idx1 = i
            } else if (value > max2) {
                max2 = value
                idx2 = i
            }
        }

        val f1 = if (idx1 != -1) idx1 * binWidth else 0f
        val f2 = if (idx2 != -1) idx2 * binWidth else 0f
        return Pair(f1, f2)
    }

    override fun onDestroy() {
        println("SERVICE DESTROYED")
        watchdogHandler.removeCallbacks(watchdogRunnable)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        sensorManager.unregisterListener(this)
        audioManager.abandonAudioFocus(this)
        releaseEchoCanceler()
        releaseAutomaticGainControl()
        exposureManager?.persist()
        AudioBridge.removeCallback(serviceCallback)
        AudioBridge.stop()
        AudioBridge.destroy()
        super.onDestroy()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("AudioMonitorService", "Audio focus gained, starting/resuming monitoring")
                startMonitoring()
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d("AudioMonitorService", "Audio focus lost, stopping monitoring")
                AudioBridge.stop()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            // Subtract gravity constant to get movement-based acceleration
            currentMovementIntensity = abs(magnitude - 9.81f)
            AudioBridge.movementIntensity = currentMovementIntensity
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateWidget(db: Float, dose: Float) {
        val prefs = getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("current_db", db)
            .putFloat("current_dose", dose)
            .apply()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                NoiseWidget().updateAll(this@AudioMonitorService)
            } catch (e: Exception) {
                // Ignore widget update errors
            }
        }
    }

    private fun manageAcousticEchoCanceler() {
        if (settingsManager?.speakerCompensationEnabled == false) {
            releaseEchoCanceler()
            return
        }

        val sessionId = AudioBridge.getSessionId()
        if (sessionId > 0) {
            if (AcousticEchoCanceler.isAvailable()) {
                releaseEchoCanceler()
                try {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)
                    echoCanceler?.enabled = true
                    Log.d("AudioMonitorService", "AcousticEchoCanceler enabled on session $sessionId")
                } catch (e: Exception) {
                    Log.e("AudioMonitorService", "Failed to create AcousticEchoCanceler", e)
                }
            }
        }
    }

    private fun manageAutomaticGainControl() {
        val sessionId = AudioBridge.getSessionId()
        if (sessionId > 0) {
            if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                releaseAutomaticGainControl()
                try {
                    automaticGainControl = android.media.audiofx.AutomaticGainControl.create(sessionId)
                    automaticGainControl?.enabled = false // Explicitly disable AGC
                    Log.d("AudioMonitorService", "AutomaticGainControl DISABLED on session $sessionId")
                } catch (e: Exception) {
                    Log.e("AudioMonitorService", "Failed to manage AutomaticGainControl", e)
                }
            } else {
                Log.d("AudioMonitorService", "AutomaticGainControl is not available on this device")
            }
        }
    }

    private fun releaseEchoCanceler() {
        echoCanceler?.enabled = false
        echoCanceler?.release()
        echoCanceler = null
    }

    private fun releaseAutomaticGainControl() {
        automaticGainControl?.enabled = false
        automaticGainControl?.release()
        automaticGainControl = null
    }

    private fun startMonitoring() {
        val result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.e("AudioMonitorService", "Could not gain audio focus, monitoring might be restricted")
        }

        val deviceId = settingsManager?.selectedMicId ?: -1
        val preset = settingsManager?.audioPreset ?: 9
        val (sensitivity, freqs, gains) = fetchMicrophoneInfo(deviceId)
        val useAWeighting = settingsManager?.useAWeighting ?: true
        val useNoiseRejection = settingsManager?.intelligentNoiseRejection ?: true
        val calibrationOffset = settingsManager?.calibrationOffset ?: 0f
        val integrationTime = settingsManager?.integrationTime ?: 0

        AudioBridge.addCallback(serviceCallback)
        if (AudioBridge.start(deviceId, preset, sensitivity, freqs, gains, useAWeighting, useNoiseRejection, calibrationOffset, integrationTime)) {
            manageAcousticEchoCanceler()
            manageAutomaticGainControl()
        }
    }

    private fun fetchMicrophoneInfo(deviceId: Int): Triple<Float, FloatArray?, FloatArray?> {
        val mics = audioManager.microphones
        val micInfo = if (deviceId != -1) {
            mics.find { it.id == deviceId }
        } else {
            // Find the built-in mic that is likely the default
            mics.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        }

        return if (micInfo != null) {
            val freqResponse = micInfo.frequencyResponse
            val freqs = FloatArray(freqResponse.size)
            val gains = FloatArray(freqResponse.size)
            freqResponse.forEachIndexed { index, pair ->
                freqs[index] = pair.first
                gains[index] = pair.second
            }
            Log.d("AudioMonitorService", "Using Mic: ${micInfo.description}, Sensitivity: ${micInfo.sensitivity}dBFS")
            Triple(micInfo.sensitivity, freqs, gains)
        } else {
            Triple(-999f, null, null)
        }
    }

    private fun sendLoudNoiseNotification(db: Double, duration: Float) {

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Loud Noise Detected")
            .setContentText("${db.toInt()} dB detected for ${String.format(Locale.getDefault(), "%.1f", duration)}s")
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 300))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(ALERT_ID, notification)
    }

    private fun sendExposureNotification(dose: Float) {

        val percentage = (dose * 100).toInt()

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Noise Exposure Alert")
            .setContentText("You have reached $percentage% of your daily NIOSH noise dose limit.")
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 300))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(ALERT_ID + 1, notification)
    }

    private fun buildForegroundNotification(): Notification {
        val stopIntent = Intent(this, AudioMonitorService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val mainActivityPendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Noise Monitoring")
            .setContentText("Listening for loud sounds")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(mainActivityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
//            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun getMicLocationString(location: Int): String {
        return when (location) {
            1 -> "Main Body"
            2 -> "Front"
            3 -> "Back"
            4 -> "External"
            else -> "Unknown"
        }
    }

    private fun logMicrophoneSpecs() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

        Log.d("AudioMonitorService", "--- Available Microphone Hardware ---")
        devices.forEach { device ->
            val typeName = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Mic"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Mic"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO Mic"
                else -> "Other (${device.type})"
            }

            val channels = device.channelCounts.joinToString(", ")
            val sampleRates = device.sampleRates.joinToString(", ")

            Log.d("AudioMonitorService", "Device: ${device.productName}")
            Log.d("AudioMonitorService", "  Type: $typeName")
            Log.d("AudioMonitorService", "  ID: ${device.id}")
            Log.d("AudioMonitorService", "  Channels: $channels")
            Log.d("AudioMonitorService", "  Sample Rates: $sampleRates")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Log.d("AudioMonitorService", "  Address: ${device.address}")
                val mics = audioManager.microphones
                mics.find { it.id == device.id }?.let { micInfo ->
                    Log.d("AudioMonitorService", "  Sensitivity: ${micInfo.sensitivity} dBFS")
                    Log.d("AudioMonitorService", "  Location: ${getMicLocationString(micInfo.location)}")
                    Log.d("AudioMonitorService", "  Freq Response Points: ${micInfo.frequencyResponse.size}")
                }
            }
        }
        Log.d("AudioMonitorService", "---------------------------------------")
    }
}
