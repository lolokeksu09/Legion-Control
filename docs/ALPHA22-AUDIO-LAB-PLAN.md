# Legion Control alpha.22 — Audio Lab: stereo, playback, routing and test history

Status: IMPLEMENTED, MERGED AND PERMANENTLY SIGNED. First commit was the plan; physical sound tests remain NOT RUN.
Base: `main` at `50c2f0271ff89674a87affb3de445961d7b22a4e` (alpha.21).
Target: Android 13+, any game/device, release signed with existing permanent certificate.

## Difference from current modules

Compatibility Lab already reads static audio-output metadata, but there is **no actual controllable audio signal generator, stereo channel test, frequency sweep, output-routing observation during playback, or audio test history**. This patch adds those; it does not reimplement static inspection or claim sound-recording capability.

## Planned functionality

1. `AudioSignalGenerator.kt`: deterministic stereo signed 16-bit PCM generator with selectable L/R/both, alternating stereo identification, low-volume frequency sweep and 220/440/880/1760 Hz tones; bounded frequencies/durations, anti-click fade in/out, no clipping. Pure Kotlin JVM tests.
2. `AudioTestBackend.kt`: Android `AudioTrack` manual playback (no microphone), user-controlled stop, foreground-only; audio focus, measured playback-head frames and Android selected-route **when exposed**; clear separation between generated signal and hardware-acoustic verification.
3. `AudioRouteInspector.kt`: attached output inventory and `AudioDeviceCallback` while Activity visible; route changes read-only and explicitly timestamped; do not infer speaker quality or claim precise Bluetooth latency.
4. SQLite v11 additive `audio_test_runs` / `audio_route_events` storing user-triggered test metadata only; safe v1–v10 migrations, transaction-safe run state and interruption recovery. No recorded user audio; no cloud.
5. `AudioLabActivity.kt`: AMOLED audio test panel, mode/channel/frequency selection, start/stop, progress, low-volume warning, audio routing cards, history, summary comparison, CSV export via Android SAF, manual sharing.
6. Tests: actual SQLite v10→v11 preservation, FK/cascade and invalid-input coverage, JVM waveform tests and offline compilation. No new Android permissions, QUERY_ALL_PACKAGES, Root or Shizuku.

## Implementation order

- [x] Branch `feat/alpha22-audio-lab` from verified alpha.21 main.
- [x] This plan committed first.
- [x] Inspect existing LocalHistoryStore v10, static AudioOutputInspector, MainActivity, Manifest, signing workflow and strict regression checks.
- [x] Backend and SQLite v11, actual signal generator and AudioTrack with lifecycle safety.
- [x] Pure Kotlin waveform tests, v10→v11 real SQLite test, preserve older regression contracts.
- [x] UI and navigation with real callbacks, SAF CSV export and notes on safe playback.
- [x] Docs and 4PDA limitations; bump versionCode to 22 and release workflow artifact labels.
- [x] PR + GitHub CI Kotlin/Rust, Android lint, signing negative tests; fix failures and rerun.
- [x] Merge after green CI and verify permanent signed release APK; supply user a direct APK file.
- [ ] Real Lenovo Legion Y70 / Motorola-ROM manual test, speaker/headphone channel identification and physical sound, Bluetooth routing and interruption behavior — NOT RUN until a device executes it.

## Safety and measurement limits

- Test signals are **original generated tones**, not copyrighted music, ≤3 seconds, restrained signal amplitude (≤12% of 16-bit full-scale) with fades; actual loudness still depends on system volume/amplifier and headphones. Warn to lower volume before play. No automatic volume-max.
- AudioTrack playback-head progress **does not establish actual acoustic latency**, sound quality, crosstalk, distortion, loudness, speaker frequency response, or accurate left-right channel placement on hardware.
- There is no microphone recording, accessibility hook, system mixer override, hidden capture, app-audio interception, VPN, network request or new permission.
- Audio focus and playback must release on leaving screen, stop immediately when user taps Stop as far as platform APIs allow. Playback runs on worker; only UI and callback registration on main.
- Connected-device inventory is not the same as active routed device; present separately.
- SQLite migrations are additive. No destructive reset, no loss of game sessions / experiments / app library, and no changes to the pinned signing fingerprint.
- Failures on limited OEM implementations must report *not available* rather than simulated measurements.

## Evidence log

- Plan-first commit: `fe50c4139d15de47b361f1a411372df11c451885` (preceded all feature code).
- Kotlin and real SQLite tests: PASS — seven generator JVM tests and executable SQLite v10→v11 preservation/FK/invalid-state checks in CI.
- Android PR and main CI: PASS — 43/43 steps in both runs; Android lint, debug assemble, Rust targets and negative signing checks.
- Permanently signed APK: PASS — pinned certificate and APK/CRC/ARM64/x86_64 contents verified; GitHub release artifact available.
- Physical sound / routing / audio focus verification on Lenovo Legion Y70: NOT RUN.

## Release verification — 2026-11-22

- PR #25: https://github.com/lolokeksu09/Legion-Control/pull/25 — merged into main as `b0d317f7b9858621285cdc62750183cde18bad69`.
- PR full CI: https://github.com/lolokeksu09/Legion-Control/actions/runs/45009859461 — SUCCESS, 43 steps.
- Main full CI: https://github.com/lolokeksu09/Legion-Control/actions/runs/45010264323 — SUCCESS, 43 steps.
- Permanent signed release: https://github.com/lolokeksu09/Legion-Control/actions/runs/45010264369 — SUCCESS, 19 steps, pinned certificate verified in workflow.
- Signed GitHub Actions artifact: `Legion-Control-v0.1.0-alpha.22-permanent-release-apk`, artifact ID `14749637283`.
- Verified original APK: `app-release.apk`, `3,587,373` bytes, SHA-256 `91eae0fd7161e6cfd51be86f9f62b9be86b4e4be67d7cf4ce9c0c771ed315b25`. ZIP CRC PASS; `AndroidManifest.xml`, `lib/arm64-v8a/liblegion_core.so` and `lib/x86_64/liblegion_core.so` found.
- **Not run on actual speaker/headphones:** installation over signed alpha.21, audible L/R identity, Bluetooth routing, media focus interruptions, CSV screen export, OEM Motorola audio handling. CI proves build/tests, not hardware-acoustic quality.
