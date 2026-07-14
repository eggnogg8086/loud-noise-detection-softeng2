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
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class AudioMonitorService : Service(), SensorEventListener {

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
    @Volatile
    private var currentMovementIntensity = 0f
    private var smoothedMovementIntensity = 0f
    private val SMOOTHING_FACTOR = 0.15f

    // Grouping logic state
    private var isLoudEpisodeActive = false
    private var episodeMaxDb = 0f
    private var episodeF1Sum = 0f
    private var episodeF2Sum = 0f
    private var episodeSampleCount = 0
    private var episodeStartTime = 0L
    private var lastLoudTime = 0L
    private val EPISODE_TIMEOUT_MS = 8000L // End episode after 8s of quiet

    private var lastDoseSampleTime = 0L
    private val DOSE_SAMPLE_INTERVAL_MS = 60000L // 1 minute

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

        // Initialize accelerometer for handling noise compensation
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        logMicrophoneSpecs()
        startMonitoring()
    }

    private fun restartMonitoring() {
        AudioBridge.stop()
        startMonitoring()
    }

    private val serviceCallback = AudioBridge.SpectrumCallback { spectrum, db ->
        val now = System.currentTimeMillis()

        // Movement-based noise compensation
        smoothedMovementIntensity = smoothedMovementIntensity * (1f - SMOOTHING_FACTOR) + currentMovementIntensity * SMOOTHING_FACTOR
        val movementCompensation = (smoothedMovementIntensity * 2.5f).coerceAtMost(25.0f)
        val compensatedDb = db - movementCompensation

        if (lastCallbackTime != 0L) {
            val deltaTimeSeconds = (now - lastCallbackTime) / 1000f
            exposureManager?.addExposure(compensatedDb, deltaTimeSeconds)
            if (exposureManager?.shouldNotify() == true) {
                sendExposureNotification(exposureManager?.getCurrentDose() ?: 0f)
                exposureManager?.markNotified()
            }
        }
        lastCallbackTime = now

        // Dose sampling
        if (now - lastDoseSampleTime > DOSE_SAMPLE_INTERVAL_MS) {
            historyManager?.addDoseSample(exposureManager?.getCurrentDose() ?: 0f)
            lastDoseSampleTime = now
        }

        val threshold = settingsManager?.thresholdDb ?: 82f

        if (compensatedDb > threshold) {
            lastLoudTime = now
            if (!isLoudEpisodeActive) {
                isLoudEpisodeActive = true
                episodeStartTime = now
                episodeMaxDb = compensatedDb
                val freqs = findTopFrequencies(spectrum)
                episodeF1Sum = freqs.first
                episodeF2Sum = freqs.second
                episodeSampleCount = 1
            } else {
                episodeMaxDb = maxOf(episodeMaxDb, compensatedDb)
                val freqs = findTopFrequencies(spectrum)
                episodeF1Sum += freqs.first
                episodeF2Sum += freqs.second
                episodeSampleCount++
            }
        } else {
            if (isLoudEpisodeActive && (now - lastLoudTime > EPISODE_TIMEOUT_MS)) {
                // End Episode
                val duration = (lastLoudTime - episodeStartTime) / 1000f
                if (duration > (settingsManager?.durationSeconds ?: 1f)) {
                    historyManager?.addEvent(
                        episodeMaxDb,
                        episodeF1Sum / episodeSampleCount,
                        episodeF2Sum / episodeSampleCount,
                        duration
                    )
                    
                    if (now - lastNotificationTime > NOTIFICATION_COOLDOWN) {
                        sendLoudNoiseNotification(episodeMaxDb.toDouble(), duration)
                        lastNotificationTime = now
                    }
                }
                isLoudEpisodeActive = false
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
        sensorManager.unregisterListener(this)
        exposureManager?.persist()
        AudioBridge.removeCallback(serviceCallback)
        AudioBridge.stop()
        AudioBridge.destroy()
        super.onDestroy()
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

    private fun startMonitoring() {
        val deviceId = settingsManager?.selectedMicId ?: -1
        val preset = settingsManager?.audioPreset ?: 9

        AudioBridge.addCallback(serviceCallback)
        AudioBridge.start(deviceId, preset)
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
            }
        }
        Log.d("AudioMonitorService", "---------------------------------------")
    }
}