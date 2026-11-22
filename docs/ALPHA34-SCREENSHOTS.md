# Legion Control alpha.34 — User-consented screenshots

## What this adds

A non-diagnostic Capture Studio extension for a **single PNG screenshot per fresh, explicit Android MediaProjection consent**. Capture Studio offers a separate screenshot action, cancel action, app-owned gallery, view/share, and delete-after-confirmation. No silent capture, root, Shizuku, Accessibility, new Android permission, broad photo access, or cloud upload.

### Flow

1. Open Capture Studio → "Сделать скриншот экрана".
2. Confirm the OS MediaProjection prompt. A dedicated mediaProjection foreground service starts before `getMediaProjection()`.
3. Switch to the target game within **5 seconds**. The first eligible image after the countdown is captured as a PNG. If the system does not provide an eligible frame within 12 seconds or revokes consent, the capture fails closed.
4. `Pictures/LegionControl/LegionShot_*.png` is written through MediaStore with `IS_PENDING=1`, compressed, then published only after success. On errors, the app-owned pending file is deleted.
5. Open "Библиотека снимков" in Capture Studio. Only images matching the app's MediaStore owner package + exact relative directory + filename format are shown. Share/view by read-granted content URI, or delete the selected owned image after explicit confirmation.

### Isolation and limits

- Existing H264 video-only recorder, album, SQLite v12, native Rust/JNI, app ID and signing configuration remain unchanged.
- The screenshot service is `exported=false`, `START_NOT_STICKY`, foreground while capturing, and releases its virtual display, reader, callback and projection when complete.
- Photo size is capped to a 1920-pixel longer edge and screenshot buffer row layout is validated. Android may capture the selected app/window rather than the entire display on newer OS versions. DRM/secure surfaces may remain black.
- Video and screenshot capture are explicitly mutually exclusive. A third-party capture app or OS sharing indicator may interfere; this implementation does not bypass any Android consent.
- The screenshot is captured after the OS confirmation and countdown, **not** necessarily the frame the user intended in a game. Actual timing is subject to system scheduling and display updates.
- A saved PNG does not imply a game FPS benchmark. No audio is added.
- Source version remains alpha.27 for compatibility with prior version-locked contract checks; version bump and signed release require a coordinated release task.

### Verification gates

- JVM policy tests for filename, bounds, orientation, rows and negative cases.
- Strict static guard for fresh consent, FGS order, one projection/display, release and MediaStore pending → PNG → publish/delete, app-owned query, no new permissions.
- Full existing Kotlin/Rust JUnit, Android lint/APK/JNI CI and Android 35 emulator smoke gate.
- Physical Lenovo/Android ROM acceptance still needed: exact screenshot of a game and orientation, denial/cancel/timeout, repeated capture requiring another consent, folder visibility, share/delete only own images, no interference with ongoing MP4 and protected app privacy.
