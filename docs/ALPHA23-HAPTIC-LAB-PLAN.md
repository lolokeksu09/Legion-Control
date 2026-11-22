# Legion Control alpha.23 — Haptic Lab (original new module)

**Plan committed before code.** Baseline main `25716391837b23479f13fcd246947197cce19000` (alpha.22). Version target `0.1.0-alpha.23` / `versionCode 23`; same package and permanent signing cert. Android 13+, no Root/Shizuku.

## Existing functionality checked
- `FrameTouchDiagnostics.kt` has app-only touch/frame monitoring — do not repeat.
- `HardwareLabBackend.kt` and `HardwareLabActivity.kt` already have 8MiB sandbox I/O test — do not repeat.
- `AudioLabActivity.kt`, `GamepadLiveActivity.kt`, `SensorCaptureBackend.kt` exist. This module tests **haptic vibrator output**, not audio, touch, gamepad or storage.

## New user-facing capabilities
1. **Haptic hardware inventory** using documented `VibratorManager` / `Vibrator` APIs: connected vibrator IDs, `hasVibrator`, `hasAmplitudeControl`, and support for predefined effects (CLICK / DOUBLE_CLICK / TICK / HEAVY_CLICK) and composition primitives where exposed. Check `Settings.System.HAPTIC_FEEDBACK_ENABLED` separately as an OS setting, not physical measurement.
2. **Five safe original vibration patterns**, synthesized as `VibrationEffect.createWaveform` with safe timings, normal VIBRATE permission and amplitude fallback when amplitudeControl false: Tap, Double Tap, Triple Pulse, Slow Pulse, Long Pulse; max 160/255 amplitude, less than 2 seconds, no repeat or loops. No custom arbitrary timings or hidden background vibration.
3. **Manual intensity selection** (Gentle / Medium / Strong) with explicit digital values, not calibrated G-force/intensity. Supported hardware may scale the requested waveform.
4. **Foreground-only playback** with Stop, screen-exit cancellation, prevention of overlapping requests, and backend thread cancellation. A short tone/vibration cannot prove its motor actually moved.
5. **SQLite v12 additive**, `haptic_test_runs`, `haptic_feedback` (run FK cascade), and `haptic_capability_snapshots`; crash recovery of RUNNING rows as INTERRUPTED; record requested pattern/duration/amplitude + API request result only. Optional user-entered perception rating FELT/NOT_FELT/UNCERTAIN and short note per test (subjective, not sensor measured).
6. **History, comparable reports and CSV**: browse past runs, compare two recent requests, record subjective feedback, 1-click inspect/save public capability snapshot, Storage Access Framework CSV export. Nothing uploaded, no microphone or recording.
7. **Safety first**: new `android.permission.VIBRATE` is an Android **normal** manifest permission; no dangerous runtime permission request. No other permission changes. Haptic feedback could be suppressed by user settings/OEM; clearly mark unknown outcome.

## Sequence and acceptance
- [x] Create feature branch and commit this plan as the *first commit*.
- [x] Implement pure `HapticPattern.kt`, written test-first for bound timings/intensity/no repeat, separate from Android backend.
- [x] Implement `HapticLabSchema.kt` SQLite v12 and `HapticLabRepository.kt` with transactional finish/cascade, error and interruption semantics, CSV injection safety and no deletion of previous history.
- [x] Implement `HapticDeviceInspector.kt` via Android documented public APIs; `HapticPlaybackBackend.kt` with user-only vibration, cancellation on app background and no endless vibration.
- [x] Add `HapticLabActivity.kt`, manifest and MainActivity navigation, system volume-independent disclosure, present feedback as subjective.
- [x] Real SQLite v11→v12 migration test preserving all prior tables and seeded rows (including alpha.22 audio history), FK/check negative tests; JVM pattern tests, retain all old regressions (update exact version and button count only).
- [x] Update both workflows, README, `docs/ALPHA23-HAPTIC-LAB.md` and versionCode 23.
- [x] PR, green Kotlin/Rust/Android assemble/lint, unsigned and temporary signing rejection, merge, permanent signing workflow, verify APK and link direct chat artifact.

## Interpretation
- API request `COMPLETED_REQUEST` means the waveform was submitted and the bounded interval elapsed **without detected exception**. It does NOT verify physical vibration, G-force, click intensity, frequency, motor health, gaming-specific haptics, latency or OEM behavior.
- Suppressed haptics (system settings, DND/OEM) may be inaudible/imperceptible; no microphone/accelerometer acoustic measurement to infer vibration.
- Device inventory is capability advertisement, not calibration.
- History saved only when user explicitly requests it, no background watchers or analytics backend.
- Physical Lenovo Legion Y70 (seller Motorola ROM), other OEM devices, actual haptic feel, auto-stop on Home, feedback persistence, CSV export and update from alpha.22: **NOT TESTED** until on real hardware.

## Evidence
- Plan-first commit: `7cf1ef16e99f9dcbcdcafcaa5f1ca8863560de31` — before any feature code. Pure HapticPattern JVM tests committed before implementation.
- CI and permanently signed APK: PASS (see verified evidence below).
- Physical test: NOT RUN; Lenovo Legion Y70/Motorola ROM and other OEM haptic sensation must be verified on hardware.

## Release verification — 22 November 2026

- **PR #26:** https://github.com/lolokeksu09/Legion-Control/pull/26, merged to main as `7c8d7b5891c17e4dda02f7c7f98686620dfecd57`.
- **Pull request CI:** https://github.com/lolokeksu09/Legion-Control/actions/runs/45013811689 — SUCCESS, **44/44** steps after fixing static range check and Android primitive constant passing; no lint baseline or contract disabled.
- **Main CI:** https://github.com/lolokeksu09/Legion-Control/actions/runs/45014232925 — SUCCESS, **44/44** steps on merged source.
- **Permanent signed release:** https://github.com/lolokeksu09/Legion-Control/actions/runs/45014232912 — SUCCESS, 19 steps, including pinned permanent signing fingerprint verification, Rust native ABIs and signed release lint.
- **GitHub artifact:** `Legion-Control-v0.1.0-alpha.23-permanent-release-apk` (artifact ID `14751023796`). Downloaded zip passed CRC; actual APK `app-release.apk` passed ZIP CRC and contains `AndroidManifest.xml`, `lib/arm64-v8a/liblegion_core.so` and `lib/x86_64/liblegion_core.so`.
- **Signed APK:** 3,652,957 bytes; **SHA-256** `a6d93fd6023e47601915c6af227a39d377a43c063d29ff6a1dfb061e6ce95862`; direct chat artifact `Legion-Control-v0.1.0-alpha.23-signed.apk`.
- **SQLite v11→v12:** real Python SQLite migration preserving 17+ prior tables including alpha.22 Audio Lab, with FK/cascade/negative SQL constraint tests, plus pure JVM tests for all five finite waveform patterns and CSV escaping.
- **Not established by CI:** live physical motor output, intensity, latency, app install over alpha.22, OEM configuration settings, vibration interruption on the physical Lenovo, or in-device CSV/dialog UX. No real-world benchmark or calibration claim.
