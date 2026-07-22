# Implementation Plan - Storage & Efficiency Polish

This plan introduces automated storage management for recording snippets and energy-saving measures for background monitoring.

## Proposed Changes

### [Component] Data Management

#### [MODIFY] [SettingsManager.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/SettingsManager.kt)
- Add `KEY_STORAGE_LIMIT_DAYS` (default `7 days`).
- Add `KEY_AUTO_CLEANUP_ENABLED` (default `true`).

#### [MODIFY] [HistoryManager.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/HistoryManager.kt)
- Add `performAutoCleanup()` method:
    - Scans the history for events older than the storage limit.
    - Deletes the associated `.wav` snippet files from `cacheDir`.
    - Removes the events from the JSON history if desired, or just cleans the heavy audio files. (Recommendation: keep JSON, delete WAV).

### [Component] Audio Service & Efficiency

#### [MODIFY] [AudioMonitorService.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioMonitorService.kt)
- **Daily Cleanup**: Run `HistoryManager.performAutoCleanup()` once every 24 hours (or upon service start).
- **Battery Safeguard**:
    - Register a `BatteryManager` receiver.
    - If battery drops below 10% (and not charging), post a notification: "Monitoring paused due to low battery" and stop monitoring.
    - Resume when charging starts.

### [Component] UI

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/SettingsScreen.kt)
- Add a new "Storage Management" section.
- Add a slider for "Keep Recordings For" (1, 3, 7, 30 days).
- Add a "Delete All Recordings" utility button (cleans WAVs without wiping history).

---

## Verification Plan

### Manual Verification
1.  **Cleanup Test**:
    - Manually set the "Keep For" limit to 1 day.
    - Record a noise event.
    - Change device system clock to tomorrow.
    - Restart app/service.
    - Verify the `.wav` file is deleted from `cacheDir` while the history log remains.
2.  **Battery Test**:
    - Use `adb shell dumpsys battery set level 5` to simulate low battery.
    - Verify monitoring stops and notification appears.
    - Set battery to 50% and plug in. Verify monitoring resumes.
3.  **Storage UI**:
    - Verify the new slider and cleanup button work as expected in Settings.
