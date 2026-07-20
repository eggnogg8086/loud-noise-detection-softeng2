# Implementation Plan - Intelligent Mic Selection & Stereo Monitoring

Enhance the audio capture pipeline to support high-fidelity stereo monitoring and provide users with detailed information about microphone hardware locations.

## User Review Required

> [!IMPORTANT]
> - **Stereo Support**: While many modern devices support stereo input, some budget or older devices are strictly Mono. The app will automatically detect and fall back to Mono if needed.
> - **CPU Usage**: Processing two channels instead of one (FFT and RMS calculations) will slightly increase CPU and battery consumption.

## Proposed Changes

### Native Audio Engine (C++)

#### [MODIFY] [AudioEngine.h](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.h)
- Update members to handle stereo buffers (interleaved data).
- Add support for per-channel RMS and Peak calculation.

#### [MODIFY] [AudioEngine.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.cpp)
- Change `AudioStreamBuilder` to request `ChannelCount::Stereo`.
- Implement interleaved sample processing in `onAudioReady`.
- Calculate `leftDb` and `rightDb` independently.
- The `mCallback` will now return the **Maximum** of the two channels as the primary dB value, plus a **Stereo Balance** factor (-1.0 for Left to 1.0 for Right).

### JNI & Kotlin Bridge

#### [MODIFY] [AudioBridge.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioBridge.cpp)
- Update the callback interface to pass stereo balance data to Kotlin.

#### [MODIFY] [AudioBridge.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioBridge.kt)
- Add `stereoBalance` volatile property.

### Audio Monitoring Service

#### [MODIFY] [AudioMonitorService.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioMonitorService.kt)
- Enhance `logMicrophoneSpecs` and `fetchMicrophoneInfo` to extract `MicrophoneInfo.getLocation()` and `MicrophoneInfo.getDeviceLocation()`.
- Implement a helper to map location constants (e.g., `LOCATION_MAINBODY_FRONT`) to user-friendly strings.

### UI Improvements

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/SettingsScreen.kt)
- Update the microphone list to display the hardware location (e.g., "Built-in Mic (Front)").

#### [MODIFY] [AudioViewModel.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioViewModel.kt)
- Add `stereoBalance` state (float).

#### [MODIFY] [LoudNoiseDetectorScreen.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/LoudNoiseDetectorScreen.kt)
- Add a subtle **Stereo Balance Meter** below the main dB reading. This will show as a horizontal bar that shifts left/right based on where the noise is loudest.

## Verification Plan

### Automated Tests
- Unit test to ensure `stereoBalance` is calculated correctly (e.g., Left-only signal results in -1.0).
- Verify fallback logic by simulating a Mono-only `AudioStream`.

### Manual Verification
- **Directional Test**: Snap fingers on the left and right sides of the phone and verify the Balance Meter responds accurately.
- **Hardware List**: Check the Settings screen to see if microphone locations are correctly identified and displayed.
- **Accuracy Check**: Ensure the primary dB reading reflects the loudest channel (Peak) rather than the average.
