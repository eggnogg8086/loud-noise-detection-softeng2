# Implementation Plan - Measurement & Notification Audit Fixes

After auditing the measurement system, I've identified a few critical improvements to ensure high accuracy and reliable notifications, especially when using the new "Slow" integration mode.

## Identified Issues

1.  **Dose Timing Jitter**: The dose calculation currently uses `System.currentTimeMillis()` to determine how much time has passed. In a background service, the OS might delay the callback, causing the app to "over-calculate" exposure if a large gap occurs.
2.  **Alert Sluggishness**: If a user sets the integration time to "Slow" (1s), the app becomes less responsive to sudden loud sounds (like a bang or a shout). The alert threshold check uses the smoothed value, meaning it might miss short, dangerous peaks.
3.  **Source Inconsistency**: While the UI correctly distinguishes between raw and filtered dB, the Service logic is slightly simplified and could benefit from using the more responsive "Fast Peak" for alerts.

## Proposed Changes

### [Component] Native Engine

#### [MODIFY] [AudioEngine.h](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.h) / [AudioEngine.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.cpp)
- Calculate a **Movement-Compensated Peak** (`dbAlert`) that ignores "Slow" integration but still filters out phone handling noise.
- Pass this new value through the JNI bridge.

### [Component] JNI Bridge

#### [MODIFY] [AudioBridge.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioBridge.kt) / [AudioBridge.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioBridge.cpp) / [SpectrumCallback.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/SpectrumCallback.kt)
- Add a new parameter `dbAlert` to the `onSpectrum` callback.

### [Component] Audio Service

#### [MODIFY] [AudioMonitorService.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioMonitorService.kt)
- **Precise Dose Timing**: Replace `System.currentTimeMillis()` logic with a constant `BUFFER_DURATION` (calculated as `FFT_SIZE / SAMPLE_RATE`). This ensures the dose is always based on the exact amount of audio processed.
- **Responsive Alerts**: Use the new `dbAlert` for threshold checks. This ensures that even in "Slow" mode, the app will instantly notify you of sudden loud sounds.

## Verification Plan

### Manual Verification
1.  **Dose Accuracy**: Verify the "Daily Dose" increments consistently even when the phone screen is off and the service is potentially throttled.
2.  **Alert Responsiveness**:
    - Set Integration Time to **Slow**.
    - Clap loudly near the phone.
    - Verify that the app **immediately** records a "Loud Noise" event and sends a notification, even though the main VU meter on the screen reacts slowly.
3.  **Handling Noise**: Rub the phone case while the integration is "Slow". Verify that no alert is triggered (Movement Compensation still works).
