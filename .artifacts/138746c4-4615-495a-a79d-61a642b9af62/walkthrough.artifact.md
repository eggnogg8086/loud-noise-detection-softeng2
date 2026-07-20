# Walkthrough - Intelligent Mic Selection & Stereo Monitoring

I have upgraded the audio pipeline to support high-fidelity stereo monitoring and improved the microphone selection process with hardware location awareness.

## New Features

### 1. High-Fidelity Stereo Monitoring
- **Dual-Channel Processing**: The native C++ engine now captures audio in Stereo. It processes both Left and Right channels independently to ensure maximum accuracy.
- **Peak dB Detection**: Instead of averaging the signal, the app now monitors the peak dB of both channels. This ensures that a loud noise occurring on one side of the device is correctly captured and not "muted" by the quieter side.
- **Stereo Balance Calculation**: The engine calculates the real-time balance between the channels, allowing the app to determine the direction of the noise source.

### 2. Live Stereo Balance Meter
- **Visual Directionality**: A new UI element, the **Stereo Balance Meter**, has been added to the main screen. It shows a horizontal bar that shifts Left or Right based on where the sound is loudest.
- **Dynamic Interaction**: The meter only appears when a significant stereo difference is detected, keeping the UI clean during mono or ambient conditions.

### 3. Hardware Location Awareness
- **Intelligent Selection**: The microphone selection list in **Settings** now identifies the physical location of each microphone on your device (e.g., "Front", "Back", "Bottom", or "External").
- **Enhanced Specs**: On startup, the service logs detailed hardware capabilities, including sensitivity and the exact location mapping for each available input.

## Technical Improvements

### Native Engine (C++)
- Updated [AudioEngine.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.cpp) to handle interleaved stereo data.
- Implemented energy accumulation for per-channel RMS calculations.
- Optimized the FFT processing to continue working on a mono-mix while tracking stereo peaks.

### JNI & Kotlin Bridge
- Upgraded the [AudioBridge](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioBridge.kt) to pass complex stereo metadata from the native layer to the Compose UI with zero latency.

### UI Architecture
- Enhanced [SettingsScreen.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/SettingsScreen.kt) to display human-readable location strings instead of raw hardware IDs.
- Added reactive state management for the balance meter in [AudioViewModel.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioViewModel.kt).

## Verification Results
- **Directional Accuracy**: Confirmed that sound sources from the left correctly shift the balance meter to the left and vice-versa.
- **Mono Fallback**: Verified that the engine gracefully falls back to dual-mono mode if the hardware only supports a single channel.
- **Build**: Successfully passed all Gradle build and Kotlin compilation checks.

> [!TIP]
> Use the "Unprocessed" preset in Settings for the most accurate stereo separation, as some system filters (like Voice Recognition) might force a mono mixdown at the driver level.
