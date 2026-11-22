# Legion Control alpha.35 — paused/resumed MP4 capture (non-diagnostic)

## New user scenario

After an explicit MediaProjection authorization, Capture Studio records a local video-only MP4. The user may now pause and continue the *same file* either from Capture Studio or the persistent foreground notification. Neither action requests a new projection token or starts a new recording.

- **Pause:** Android `MediaRecorder.pause()` is called only after `MediaRecorder.start()`. The active-duration auto-stop timer is suspended; no additional encoded video time is counted.
- **Continue:** `MediaRecorder.resume()` keeps the same recorder/surface/MediaProjection and reinstates the **remaining** active-duration limit.
- **Stop:** manual stop works from either recording or paused states; finishing uses active elapsed time (rather than paused wall time) to reject empty or too-short MP4 files.
- **Maximum continuous pause: five minutes.** The foreground projection is stopped even if the user never resumes; valid existing material is saved normally, and an invalid/empty MP4 is deleted.
- **Codec failure:** a thrown pause/resume exception ends the projection; existing MediaStore pending/publish/delete logic decides whether a valid clip can be committed.
- **Status:** clear recording/paused labels, notification toggle and distinct Pause/Resume actions. Duplicate or stale control commands are ignored.

The implementation uses `SystemClock.elapsedRealtime()` via a pure Kotlin `CaptureRecordingClock`, **not** `System.currentTimeMillis()` or assumptions about OEM FPS/codec timing.

## Compatibility / permissions

No new Android permissions, Root, Shizuku, Accessibility, boot receiver, background screen monitoring, cloud operations, microphone or game audio. Existing screen-recording consent, `CaptureRecordingService` foreground service, app-owned `Movies/LegionControl/` MediaStore MP4, PNG capture, SQLite v12, Rust/JNI ABI, signing configuration and application ID are unchanged. The native recorder uses Android's public pause/resume calls (API 24+, minimum project API 33).

No new or changed playback/video editor; no claimed forced game speed or bypass of OEM capture restrictions. A codec/firmware might reject pause/resume despite the public SDK contract.

## Verification

- Unit `CaptureRecordingClockTest`: time freezes while paused; pause/resume cycles; remaining duration and zero bound; invalid clock rollback; max continuous pause constant; paused-stop duration.
- `scripts/check_alpha35_capture_pause_resume.py`: controls, notification PendingIntent actions, strict no-privilege policy, capture lifecycle unchanged.
- Existing workflow: all static contracts + JVM tests + native Rust build + APK/lint + release-cert safety + Android 35 install/smoke.

**Device scenarios still necessary:** actual pause/resume MP4 continuity on Lenovo Legion Y70 with Motorola ROM; notification actions while inside Wild Rift; 1/5/15-minute presets; pause at near-deadline; stop while paused; 5-minute pause timeout; rapid duplicate controls; missing notifications/denied POST_NOTIFICATIONS; MediaProjection revoked while paused; encoder failure, no leftover pending MP4. A successful compile/emulator smoke cannot substitute for physical codec and video playback checks.

Reference: https://developer.android.com/reference/android/media/MediaRecorder#pause() and #resume()
