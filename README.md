# Loud Noise Detection

An Android application for real-time environmental noise monitoring, sound-level visualization, and daily noise exposure tracking.

The application continuously monitors surrounding sound through the device microphone, estimates noise levels, displays frequency information, calculates daily exposure using the NIOSH noise-dose model, and alerts the user when loud noise is detected.

## Features

* Real-time noise level monitoring
* Current sound level in decibels (dB)
* Maximum detected sound level
* Real-time frequency spectrum / spectrogram
* Background microphone monitoring
* Foreground service support
* Loud noise notifications
* Daily noise exposure tracking
* NIOSH-based noise dose calculation
* Persistent exposure data
* Automatic daily dose reset
* Configurable settings
* Battery optimization handling
* Native audio processing using JNI

## Noise Level Classification

The application categorizes the detected environmental noise level into simple descriptions.

| Sound Level      | Description    |
| ---------------- | -------------- |
| Below 40 dB      | Very Quiet     |
| 40–54 dB         | Quiet          |
| 55–69 dB         | Moderate       |
| 70–84 dB         | Loud           |
| 85–99 dB         | Very Loud      |
| 100 dB and above | Extremely Loud |

## NIOSH Daily Noise Exposure

The application estimates accumulated daily noise exposure using the NIOSH Recommended Exposure Limit.

NIOSH uses:

* 85 dBA as the recommended 8-hour exposure level
* A 3 dB exchange rate
* Every 3 dB increase halves the recommended exposure time

The application calculates the increase in daily dose using:

```text
ΔDose = Δt × 2^((L - 85) / 3) / 28800
```

Where:

* `ΔDose` = increase in daily noise dose
* `Δt` = exposure duration in seconds
* `L` = measured sound level
* `28800` = 8 hours in seconds

A daily dose of approximately:

```text
100%
```

represents the recommended maximum daily exposure under the NIOSH model.

## Technology Stack

### Android

* Kotlin
* Jetpack Compose
* Android ViewModel
* Foreground Services
* Android Notifications

### Audio Processing

* C / C++
* JNI
* Native audio capture
* Fast Fourier Transform (FFT)
* Real-time spectrum processing

## Audio Configuration

The application currently uses the following audio-processing configuration:

```text
Sample Rate:      44100 Hz
FFT Size:         8192 samples
Frequency/Bin:    ~5.383 Hz
Buffer Duration:  ~0.186 seconds
```

The approximate FFT frequency resolution is:

```text
44100 / 8192 ≈ 5.383 Hz/bin
```

## Application Architecture

The application separates audio capture, processing, exposure tracking, background monitoring, and the user interface into different components.

### AudioViewModel

`AudioViewModel` manages real-time sound information used by the interface.

Important values include:

```kotlin
currentDb
maxDb
dailyDose
```

It receives processed audio information and updates the Jetpack Compose interface.

### AudioBridge

`AudioBridge` provides communication between the Kotlin application and the native audio-processing library through JNI.

The main native functions include:

```kotlin
nativeInit(callback)
nativeStart(deviceId, inputPreset)
nativeStop()
nativeDestroy()
```

Processed spectrum and sound-level information is returned through:

```kotlin
onSpectrum(
    spectrum: FloatArray,
    db: Float
)
```

### AudioMonitorService

`AudioMonitorService` handles continuous background noise monitoring.

It runs as an Android Foreground Service so monitoring can continue when:

* The application is minimized
* Another application is opened
* The device screen is turned off
* The phone is locked

### ExposureManager

`ExposureManager` handles daily noise exposure calculations.

It is responsible for:

* Calculating exposure from measured sound levels
* Accumulating daily noise dose
* Saving the current exposure
* Restoring previous exposure values
* Detecting when a new day begins
* Resetting the daily exposure

### SettingsManager

`SettingsManager` stores application settings and persistent values such as daily exposure information.

## System Flow

```text
Microphone
    |
    v
Native Audio Capture
    |
    v
Audio Buffer
    |
    v
FFT / Sound-Level Processing
    |
    v
JNI Callback
    |
    v
AudioViewModel
    |
    +------------------+-------------------+
    |                  |                   |
    v                  v                   v
User Interface    ExposureManager    Noise Detection
    |                  |                   |
    v                  v                   v
Display           Daily Dose          Notification
```

## Spectrogram

The application performs a Fast Fourier Transform on captured audio.

The FFT converts microphone data from the time domain into the frequency domain, allowing the application to determine the strength of different frequencies in the surrounding environment.

The resulting spectrum can then be displayed in real time.

## Background Monitoring

Noise monitoring can continue while the application is not actively visible.

Android requires this functionality to run through a foreground service.

The foreground notification also informs the user that microphone monitoring is currently active.

## Loud Noise Notifications

When detected noise exceeds the configured threshold, the application can display a warning notification.

A notification cooldown is used to prevent alerts from repeatedly appearing within a short period.

## Purpose

The purpose of the project is to create an accessible mobile noise-monitoring system capable of increasing awareness of potentially harmful environmental noise.

The application combines:

* Real-time microphone monitoring
* Noise-level estimation
* Frequency analysis
* Daily exposure calculations
* Background monitoring
* User notifications

These features allow users to better understand both immediate sound levels and their accumulated noise exposure throughout the day.

## Future Improvements

Planned or possible improvements include:

* Device microphone calibration
* Improved sound-level accuracy
* Multiple microphone support
* Historical exposure graphs
* Daily and weekly exposure reports
* Data export
* Improved lock-screen alerts
* Long-term monitoring statistics
* Device-specific calibration profiles
* Cross-platform support

## Disclaimer

This application is intended for educational, research, and informational purposes.

Smartphone microphones are not calibrated sound-level meters. Measurements may vary depending on:

* Device model
* Microphone hardware
* Automatic gain control
* Manufacturer audio processing
* Microphone position
* Environmental conditions

The application should not be considered a replacement for a professionally calibrated sound-level meter or occupational noise dosimeter.

## License

This project was developed for academic and research purposes.
