# Legion Control alpha.25 — Capture Studio (functional screen clips)

**Baseline:** main `21e00089e0353b9b877deebf465dcd26747d2e6c` / alpha.24. **Plan-first:** first branch commit before any feature code. Target `0.1.0-alpha.25` `versionCode=25`. Keep application ID `dev.lolokeksu.legioncontrol`, permanent signer, Rust JNI, SQLite v12 and all previous data.

## Repository audit & decision
Existing app already has:
- Game Session telemetry (not video), Game Toolbox opt-in overlay, Experiment Lab session notes, Haptic & Audio labs, Application Center.
- No user-consented MediaProjection + MediaRecorder MP4 workflow or owned-video library.
**New module:** Capture Studio, a local video capture and clip management studio for *any* game/app, not just Wild Rift. No FPS booster, kernel control, emulator, overlay input interceptor or data collection.

## Outcomes and acceptance criteria
1. **CAP25-01 Consent.** Dedicated `CaptureStudioActivity` asks Android via `MediaProjectionManager.createScreenCaptureIntent()` **on every attempt**. No caching/reusing token (Android 14+ requirement). Consent refusal performs no service start and creates no media item.
2. **CAP25-02 Actual capture.** Explicitly started non-exported `CaptureRecordingService` with type `mediaProjection` and `FOREGROUND_SERVICE_MEDIA_PROJECTION` after consent. Service calls `startForeground(...FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)` **before** `getMediaProjection()`; registers `MediaProjection.Callback` **before** `createVirtualDisplay()`; one projection/VD per consent, no covert start/boot receiver. MediaRecorder H.264 MP4, video only, no microphone or app audio.
3. **CAP25-03 User presets.** Choose validated 720p/1080p long-edge cap, 30/60 *requested* frames/s, 4/8/12 Mbps, 1/5/15 minute max recording time. Aspect ratio preserved, dimensions even; API/codec/ROM may cap actual quality; never claim measured fps or guarantee 1080p/60.
4. **CAP25-04 Storage durability.** App-owned video inserted with MediaStore `Movies/LegionControl/` and `IS_PENDING=1`; commit on successful `MediaRecorder.stop()` after nonzero session, delete partial/broken pending rows on exceptions, token revocation or recording failure. No broad storage permissions or hidden files. Explicit stop in persistent notification and Activity. Notification and service state reflect real outcome. `START_NOT_STICKY` and maximum duration enforced in process.
5. **CAP25-05 Clip library.** Query only `OWNER_PACKAGE_NAME=dev.lolokeksu.legioncontrol` and specified relative directory. Show list (name, size, date, duration when provided); system viewer intent, share via read-grant, delete with confirmation. No automatic uploads, backups or unrelated-file access. Video deletion only with direct explicit user choice; prior Android telemetry database untouched.
6. **CAP25-06 Lifecycle/privacy.** Handle `MediaProjection.Callback.onStop` system revoke, repeated stop, service destroy, rotation (fixed-size output may crop/letterbox on capture resize). Foreground service can be stopped via notification; don't re-use the token. Explain that Secure/DRM/FLAG_SECURE surfaces may be blank, device can restrict captures, and captured image may include notifications/personal content. No microphone, location, Accessibility or Shizuku/Root.
7. **CAP25-07 Verification.** Pure JVM tests for encoded size rounding, preset validation, recording time bounds, names, and no out-of-range resource values; static contract check for MediaProjection order/FGS type and pending MediaStore delete/commit; old full CI (Android assemble/lint, Rust, lossless SQLite, pinned signer) retained. **Physical Lenovo Motorola ROM + other Android device testing required** to establish real file encoding, full-screen vs single-app consent, protected-game behavior, notifications and share/delete.

## Sequence
1. Create isolated branch; commit this plan first. Add Kotlin pure configuration with JVM tests.
2. Implement Activity/Service/MediaStore-backed library; wire manifest/menu/version. Keep existing service first and existing permissions intact.
3. Add tests/static safety invariants, update exact-version/permission checks, both workflows and README; no changes to Rust or SQLite.
4. Run PR CI, fix failures, merge only on verified green final head; publish permanently signed main APK (same pinned key).
5. Report executable evidence separately from hardware unverified cases; no invented recording samples.

## Risks / explicit limits
- On Android 14+ capture tokens are one-time: never cache them, never call `createVirtualDisplay` twice on the same MediaProjection.
- MediaProjection startup must follow system consent -> mediaProjection foreground service -> projection -> callback -> virtual display. Android 15+ boot startup prohibited; no boot receiver.
- MediaRecorder may fail on constrained codecs, Surface/rotation, a stopped system projection or too-short recording; delete failed rows rather than treat partial MP4 as valid.
- App won't capture game audio/mic; no unjustified `RECORD_AUDIO` or MediaCapture hidden APIs. User chooses whether screen contains sensitive content.

## Tracking
- [x] Repo capability audit and platform API references verified (Android Developers MediaProjection guide).
- [x] Plan committed as branch's first change.
- [ ] Implementation and Kotlin tests.
- [ ] Static and whole CI passed.
- [ ] Merge/sign and artifact verified.
- [ ] Physical device acceptance (separate).
