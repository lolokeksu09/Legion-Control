# Alpha.36 — Internal game playback audio (user opt-in)

## What the feature actually does

Capture Studio now offers two explicit recording modes:
- **Video without sound (default)**: the existing `MediaRecorder`/MediaProjection MP4 path. No audio permissions prompt.
- **Video + permitted internal game/media audio**: the user opts in, Android grants `RECORD_AUDIO` if needed, then the user separately approves a **fresh MediaProjection request**. The app never calls any microphone source API; `RECORD_AUDIO` is the Android requirement for `AudioPlaybackCaptureConfiguration`.

The recording backend uses `AudioPlaybackCaptureConfiguration.Builder(mediaProjection)` with only `USAGE_GAME` and `USAGE_MEDIA`. `AudioRecord` reads PCM from playback, `MediaCodec` produces AAC-LC (48kHz stereo, 128kb/s) and an app-private temporary M4A. The existing H.264 `MediaRecorder` still creates the video MP4.

After the user stops recording, pauses too long, or Android revokes projection, a worker finalizes the AAC file and copies audio/video tracks into one MP4 via `MediaExtractor` + `MediaMuxer`. The remux destination stays `IS_PENDING=1` until fully published. The temporary M4A and intermediate MP4 are cleaned after a successful merge; if audio capture or remux fails, the **original video-only MP4** is preserved and the failure is reported instead of inventing sound.

MediaRecorder video pause/resume is kept; the audio reader stops/restarts for the same session. Encoded PCM timestamps exclude pauses. The audio muxer track start is delayed only by the observed gap between video start and audio capture start. Small codec/ROM synchronization drift remains possible.

## Consent and scope

- No Root, Shizuku, privileged audio routing, accessibility service, microphone source, always-on recording, background discovery, network upload, or new storage permission.
- Android displays a permission named “Record audio” even though playback capture does not use the microphone. Permission denial means recording with sound will not start; user can select the video-only mode instead.
- The player, its manifest and its AudioAttributes/AudioManager capture policy may prohibit playback capture. Only media/game usage and apps in the same user profile with eligible capture policy can be recorded. **Permission grant does not prove actual non-silent game audio.**
- DRM/secure video and OEM-specific playback routing may yield empty or silent captured output. It is prohibited to bypass those controls.
- Microphone/chat audio from voice communication usage is *not included*. The UI must not claim it is.
- All audio capture occurs in an explicitly user-initiated `mediaProjection` foreground service with bounded recording duration. The source AAC is held in app cache and removed after finishing; no broad library access.

## Risk and validation

The hybrid MediaRecorder + AudioRecord + AAC + MediaMuxer pipeline is materially more complicated than the previous video-only path. Passing static checks and APK compilation **is not** evidence of correct audio on Lenovo Y70.

Required physical acceptance:
1. With audio mode OFF, deny RECORD_AUDIO entirely: video-only recording should work as before.
2. With audio mode ON, deny RECORD_AUDIO: recording must not start silently; user can turn audio off.
3. Grant RECORD_AUDIO, accept fresh screen capture, start a game playing recordable media: resulting MP4 should contain H264 + AAC tracks and audible sound aligned with video.
4. Confirm no microphone/chat audio is captured and audio from an explicitly non-capturable app is absent.
5. Pause and resume video several times, confirm MP4 audio/video timeline; test stop while paused, 5-minute pause timeout, Android revoking MediaProjection, codec initialization failure and rapid duplicate actions.
6. Verify no pending/partial MP4 in `Movies/LegionControl`, no leaked temp M4A in app cache, and no unsolicited capture after force stop.
7. Run on actual Lenovo Y70/Motorola firmware; Android 35 emulator smoke is a general navigation/startup test, **not** game playback audio acceptance.

Existing SQLite v12, Kotlin/Rust JNI, appId/signing and fixed alpha.27 build metadata are unchanged. This source patch is **not** a signed release APK.

Official platform references:
- https://developer.android.com/media/platform/av-capture
- https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration
- https://developer.android.com/reference/android/media/MediaMuxer
