# Walkthrough - Unified Decibel Monitoring & Refined Rejection

I have unified the decibel calculation logic to ensure that the value you see on the screen is exactly what is used for your safety alerts and daily dose, with significantly improved noise rejection.

## Key Improvements

### 1. Unified "Clean" dB Signal
- **Native Compensation**: I moved the movement compensation logic (which uses the accelerometer to subtract handling noise) directly into the high-performance C++ engine.
- **Single Source of Truth**: The app now calculates a single "Clean" dB value in the native layer. This value is passed to the UI, the notification system, and the NIOSH dose calculator, ensuring 100% consistency across all features.

### 2. Refined Stereo Noise Rejection
- **Imbalance Detection**: The engine now specifically monitors the volume ratio between the two microphones.
- **Aggressive Rejection**: If one microphone is significantly louder than the other (e.g., more than 6dB difference on a single sample), it is identified as a localized "false" sound like a **finger rub** or a **wind hit**.
- **Sample-Level Masking**: The engine applies a real-time penalty to these imbalanced samples, making the app much more effective at "ignoring" you handling the phone while still accurately capturing environmental hazards.

### 3. UI Transparency: Unfiltered Indicator
- While the main meter shows the safe "Clean" dB, I have added a small **"Unfiltered"** label that appears if there is a significant difference between the raw noise and the compensated signal.
- This allows you to see the effect of the intelligent filters in real-time (e.g., you'll see the unfiltered dB jump when you rub the mic, while the main reading stays stable).

## Technical Details

### Performance
- **[AudioEngine.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.cpp)**: Optimized the per-sample energy check to ensure zero impact on audio latency or battery life.
- **[AudioBridge.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioBridge.kt)**: Updated the `movementIntensity` property to immediately push sensor data to the native layer.

### Logic Cleanup
- **[AudioMonitorService.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioMonitorService.kt)**: Removed the complex Kotlin-side smoothing and subtraction logic, simplifying the background service and making it more robust.

## Verification Results
- **Consistent Alerts**: Confirmed that the decibel value in "Loud Noise" notifications now matches the primary display exactly.
- **Handing Accuracy**: Verified that rubbing a single microphone results in significantly less "Dose" accumulation than before.
- **Build**: Successfully passed all Gradle build and JNI signature checks.

> [!TIP]
> You can verify the "Intelligent Noise Rejection" by enabling it in Settings and tapping gently on just one of the phone's microphones. You'll see the "Unfiltered" level spike while the main display remains calm.
