# Implementation Plan - Stereo VU Meter & Differential Rejection UI

Split the single VU meter into a stereo (Left/Right) version and add a visual indicator (blue highlighting) when the Intelligent Noise Rejection algorithm is actively filtering differential noise.

## User Review Required

> [!IMPORTANT]
> - **Stereo VU Meters**: The main screen will now feature two vertical/horizontal meters for Left and Right channels.
> - **Differential Rejection Indicator**: When the algorithm detects a significant imbalance (e.g., handling noise on one mic) and applies a penalty, the affected VU bar(s) will highlight in **Blue** to signal active filtering.
> - **Redundancy Cleanup**: The existing "Stereo Balance" bar will be removed as the dual VU meters provide this information more intuitively.

## Proposed Changes

### Native Layer (C++)

#### [MODIFY] [AudioEngine.h](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.h)
- Update `SpectrumCallback` signature to: `std::function<void(const float*, int, float, float, float, float, float, bool, int)>`.
- Add `mBufferRejectionActive` member.

#### [MODIFY] [AudioEngine.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.cpp)
- Calculate dB levels for Left and Right channels using the current sensitivity and weighting context.
- Set `mBufferRejectionActive` to true if the differential ratio exceeds the threshold during the buffer.
- Pass `dbL`, `dbR`, and `rejectionActive` to the callback.

#### [MODIFY] [AudioBridge.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioBridge.cpp)
- Update `nativeInit` method ID lookup with the new signature `([FFFFFZI)V`.
- Update the callback invocation logic to pass the 3 new parameters.

### App Layer (Kotlin)

#### [MODIFY] [AudioBridge.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioBridge.kt)
- Update `SpectrumCallback` interface: `fun onSpectrum(spectrum: FloatArray, db: Float, dbRaw: Float, dbL: Float, dbR: Float, balance: Float, rejectionActive: Boolean, channelCount: Int)`.

#### [MODIFY] [AudioMonitorService.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioMonitorService.kt)
- Update `serviceCallback` to handle the new parameters.

#### [MODIFY] [MicDiscoveryViewModel.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/MicDiscoveryViewModel.kt)
- Update `callback` to handle the new parameters.

#### [MODIFY] [AudioViewModel.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioViewModel.kt)
- Add `leftDb`, `rightDb`, and `isRejectionActive` as observable states.
- Update `callback` to populate these states.

### UI Components

#### [MODIFY] [VUMeter.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/VUMeter.kt)
- Refactor `VUMeter` to support a "stereo" mode or add a `StereoVUMeter` composable.
- Implement the **Blue** color override for the level bars when `rejectionActive` is true.

#### [MODIFY] [LoudNoiseDetectorScreen.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/LoudNoiseDetectorScreen.kt)
- Replace the single VU meter and the old balance meter with the new `StereoVUMeter`.

## Verification Plan

### Automated Tests
- Run `gradle build` to verify interface consistency across JNI and Kotlin.

### Manual Verification
- **Stereo Test**: Verify independent L/R response.
- **Rejection Test**: Rub a mic, confirm the bar turns blue and the main dB reading remains relatively stable/low.
- **Layout Check**: Ensure the new meters fit well on different screen sizes.
