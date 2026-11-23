# alpha.39 — Legion Replay Engine, opt-in video + game playback AAC

## User workflow

1. Select an installed game in Game Toolbox and configure its recording profile in Capture Studio. Resolution is 1280/1920 on the longer edge, video is requested at 30/60 FPS with 4/8/12 Mbps. Playback sound is separately opt-in. The operating system and game determine actual capture support.
2. In Capture Studio select Start Replay and a 30/60/120 second retention window. Android requests a fresh MediaProjection consent for every session and, if opted into sound, RECORD_AUDIO. The application does not capture a microphone.
3. Play with the foreground replay session running. The H.264 video packets are retained in a bounded 80 MiB in-memory ring and, when permitted, AAC packets from the selected game's UID in a separate 8 MiB audio ring.
4. Save a recent clip by pressing Replay Save in Game Toolbox, notification or Capture Studio. Frozen encoded video and audio snapshots are muxed to a new app-owned MP4 in Movies/LegionControl/. Capture continues. An AAC failure triggers a new video-only export instead.
5. Stop manually. Unsaved buffers are dropped. No Android consent token is persisted.

## Implementation

- ReplayCaptureService: user-started foreground MediaProjection service, exactly one VirtualDisplay and H.264 surface MediaCodec instance per session. Handles permission revocation, memory/thermal safety and user stop.
- ReplayFrameBuffer: validates nonempty H.264 access units, keyframe-safe eviction, strictly ascending PTS, 30/60/120 second window and 80 MiB cap.
- ReplayAudioCapture: optional AudioPlaybackCaptureConfiguration restricted to the selected currently launchable app's UID and USAGE_GAME/USAGE_MEDIA; AAC encoder and bounded packet ring. Source is playback audio, never MediaRecorder microphone.
- ReplayAudioBuffer/ReplayAudioTimeline: 8 MiB max; audio PTS uses System.nanoTime near PCM read and is mapped to encoder video timestamps using a measured delta at the first video output. Alignment is approximate; it does not claim sample-accurate synchronization.
- ReplayExporter: MediaMuxer app-owned MP4 with monotonic video/audio track timestamps. A failed mux deletes its pending MediaStore entry. Existing videos and screenshots are not touched. Prior Capture Studio still records separately.
- Game Toolbox has explicit Replay Save and settings controls; existing capture modes are mutually exclusive.
- No new manifest permissions, Root, Shizuku, Usage Access collection, hidden OEM APIs, SQLite migration, Rust/JNI ABI change or automatic recording at boot.

## Limits and correctness

Audio capture is optional. Some games forbid Android AudioPlaybackCapture, or play silence after filtering. For those cases the MP4 contains video only; the microphone is never used as a fallback. The interface reports whether an AAC track was included, not whether its samples were audible. The game's audio policy and real synchronization require physical verification.
Video frame rate is requested, not measured game FPS. The buffer length is a maximum, not a guaranteed exact interval. An H.264 I-frame boundary and byte cap may shorten a clip. 120 seconds at 12 Mbps may exceed the 80 MiB cap; use 1280/30/4 Mbps initially. The encoder pipeline consumes CPU/GPU and battery resources and may impact the game. Protected screen content and rotation depend on Android.
The service has an explicit 60 minute lifetime limit, monitors Android severe thermal and memory pressure, and does not resume itself after process death.
The source versionCode remains pinned at 27 pending coordinated release publication. No physical Legion Y70 validation is implied by emulator startup or static CI.

## Verification

- JVM: H.264 keyframe/PTS/window/memory rules, AAC bound/window/invalid PTS and snapshot persistence.
- CI: Kotlin/Android compile, Rust native tests, all existing SQLite/permission/MediaProjection regression guards and alpha.39 integration guard.
- Physical device acceptance: deny and grant MediaProjection, deny RECORD_AUDIO and retry, 3+ min running session, 30/60/120 clips, save twice while capture remains active, verify playable MP4 audio/video in a real game, verify sound silence fallback, user switching game while consent shown, DND/app overlays, other-capture exclusion, stop/revoke, process death and low-storage failure. Measure codec performance, thermal load and audio/video drift on Legion Y70 with Motorola firmware.
