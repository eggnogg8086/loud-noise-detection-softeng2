# Walkthrough - Storage & Battery Optimization

I have implemented a set of background optimizations to ensure the app manages your phone's storage and battery life responsibly.

## Changes

### 1. Smart Storage Management

#### [HistoryManager.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/HistoryManager.kt) & [SettingsScreen.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/SettingsScreen.kt)
- **Auto-Cleanup**: Added a system that automatically scans your history and deletes old `.wav` recordings to save space.
- **User Control**: You can now choose how long to keep audio clips (1, 3, 7, or 30 days) via a new "Storage Management" section in Settings.
- **Manual Purge**: Added a "Delete All Audio Recordings" button. This lets you reclaim storage instantly by wiping all heavy audio files while keeping your decibel data and history log intact.

### 2. Battery Safeguard

#### [AudioMonitorService.kt](file:///C:/Users/juanp/Documents/loud-noise-detection-softeng2/app/src/main/java/com/loudnoisedetectionapp/AudioMonitorService.kt)
- **Low Battery Protection**: The app now monitors your phone's battery level in real-time.
- **Auto-Pause**: If your battery drops below **10%** and you aren't charging, the app will gracefully stop monitoring and notify you. This prevents the app from accidentally draining your last bit of power.
- **Auto-Resume**: Monitoring automatically restarts the moment you plug your phone into a charger or the battery recovers.

## Verification Results

### Manual Verification
- **Storage Limit**: Set the limit to 1 day and verified (via clock simulation) that recordings older than 24 hours are successfully purged from the cache.
- **Manual Purge**: Verified that clicking "Delete All Audio Recordings" clears the `cacheDir` but leaves the history entries visible in the UI.
- **Battery Pause**: Simulated low battery via ADB and confirmed the app posts a "Paused" notification and stops the microphone stream as expected.
- **Battery Resume**: Verified that plugging in the device immediately restores active monitoring.
