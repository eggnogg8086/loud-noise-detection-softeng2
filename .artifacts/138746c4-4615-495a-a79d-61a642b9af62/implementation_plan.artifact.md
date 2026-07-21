# Implementation Plan - Accurate Noise Rejection & Consistent UI

Ensure that the decibel value shown in the UI, used for notifications, and calculated for the NIOSH dose is the most accurate "Clean" value, incorporating both stereo noise rejection and device movement compensation.

## User Review Required

> [!IMPORTANT]
> - **Unified Compensation**: The main dB meter will now show the final "Clean" value after all digital filtering (Stereo Rejection + Movement Compensation).
> - **Sensitivity Increase**: The "Intelligent Noise Rejection" in the native engine will be made more aggressive to better filter out finger rubs and wind.
> - **Consistent Data**: Notifications and Daily Dose will now be 100% synchronized with the value shown on the main screen.

## Proposed Changes

### Native Audio Engine (C++)

#### [MODIFY] [AudioEngine.h](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.h)
- Add `mMovementIntensity` member.
- Add `setMovementIntensity(float intensity)` method.

#### [MODIFY] [AudioEngine.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioEngine.cpp)
- **Refined Stereo Rejection**:
    - Compare per-channel RMS levels. If the ratio between channels exceeds 6dB, apply a spatial rejection penalty.
    - This is much more effective than the current Mid-Side comparison for rejecting finger rubs on a single mic.
- **Movement Compensation**:
    - Integrate the movement-based attenuation directly into the C++ `dbSPL` calculation: `dbSPL -= (mMovementIntensity * 2.5f)`.
    - This ensures the value emitted by the native engine is already fully compensated.

#### [MODIFY] [AudioBridge.cpp](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/cpp/AudioBridge.cpp)
- Add `nativeSetMovementIntensity` to pass the sensor data down to the engine.

### Audio Bridge & Service

#### [MODIFY] [AudioBridge.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioBridge.kt)
- Update `movementIntensity` setter to call the native method.

#### [MODIFY] [AudioMonitorService.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioMonitorService.kt)
- Remove the Kotlin-side movement compensation logic.
- Directly use the `db` value received from the callback for all logic (Dose, Alerts, Episodes).

### UI & ViewModel

#### [MODIFY] [AudioViewModel.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioViewModel.kt)
- Ensure `currentDb` uses the compensated value from the callback.
- Add a "Raw" dB indicator (smaller, grey text) if the user is in "Calibration" mode or advanced settings, to allow for hardware verification.

## Verification Plan

### Manual Verification
- **The Finger Test**: Rub one mic. Verify the main dB reading stays low (rejected by stereo logic).
- **The Shake Test**: Shake the phone. Verify the main dB reading drops (compensated by movement logic).
- **Notification Check**: Trigger a loud noise alert and verify the dB in the notification matches the large number shown on the main screen.
- **Dose Check**: Verify the % Dose increases according to the *clean* signal, not the raw noise.
