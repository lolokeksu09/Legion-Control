# alpha.39 — Legion Replay Engine (video-only vertical slice)

## User workflow

1. Choose a game in Game Toolbox and, optionally, set its recording quality in Capture Studio. Replay reuses alpha.38 per-game profiles: 1280/1920 long edge, requested 30/60 FPS and 4/8/12 Mbps.
2. In Capture Studio choose START REPLAY and a 30/60/120 second window. Android requests a fresh MediaProjection consent for this single session.
3. Leave the user-visible foreground service running while playing. The app retains a bounded window of already encoded H.264 frames in private process memory.
4. Tap REPLAY SAVE in Game Toolbox, the foreground notification or Capture Studio. The service freezes its compressed frame snapshot and writes a separate MP4 under Movies/LegionControl/ without stopping capture.
5. Stop from the notification or app. Unsaved frames are discarded. No projection consent is persisted.

## Components

- ReplayCaptureService: one MediaProjection and one VirtualDisplay per explicit session, one H.264 MediaCodec input surface, separate encoder drain and export threads.
- ReplayFrameBuffer: strict PTS validation, keyframe-safe eviction and 80 MiB hard memory cap. Window duration is an upper bound, not a guaranteed length.
- ReplayExporter: writes encoded snapshots to independent MP4 using MediaMuxer; pending MediaStore entries are deleted on any failed export.
- Existing CaptureClipLibrary and Clip Workshop automatically see completed MP4 replays.
- Recording, Screenshot and Replay modes guard against concurrent launch.
- No new permissions, Root, Shizuku, SQLite migrations, JNI ABI changes, hidden OEM APIs, network calls or microphone capture.

## Explicit limitations

- Audio is NOT yet implemented in Replay. Existing normal MP4 recording with game audio remains supported. Replaying audio requires synchronized bounded AAC packet buffering and a transactional two-track exporter; the existing alpha.36 audio recorder cannot be safely reused as a rolling audio track without a redesign.
- Requested 60 FPS is not in-game FPS, and recording can affect game performance.
- Encoder keyframe cadence and memory cap can shorten the actually saved tail. The status reports actual clip duration. At 12 Mbps a 120s window may be shortened by the hard cap; use 1280/30/4 Mbps for a longer retention window.
- Screen rotation and restricted/DRM content depend on Android MediaProjection; no privileges are bypassed.
- Source versionCode remains pinned to 27 pending a separate coordinated release bump. CI or emulator startup is not physical Legion Y70 capture verification.

## Acceptance checks

- JVM unit tests must exercise keyframe starts, PTS validation, memory and time eviction, stable frozen snapshots and reset.
- Existing Kotlin/Android Gradle, Rust native and signing checks remain. GitHub Actions executes the alpha.39 source wiring guard.
- On Lenovo Legion Y70: verify fresh consent and denial, start, 3+ minutes continuous capture, save multiple playable MP4 clips without interrupting capture, Game Toolbox save, other-capture exclusion, revoke/stop, process death, low memory and free storage, encoder refusal, physical 30/60 FPS performance and device heat.

No silent recording, no boot receiver and no claim of CPU/GPU or in-game FPS improvements.
