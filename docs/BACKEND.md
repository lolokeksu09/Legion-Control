# Legion Control backend · v0.1.0-alpha.4

## First working vertical slice
1. Kotlin Activity reads real battery percentage and temperature from Android ACTION_BATTERY_CHANGED, plus display mode from DisplayManager.
2. Kotlin invokes Rust via a native JNI call; there is no Kotlin fallback that pretends to be Rust.
3. Rust validates 0–100% battery, deci-Celsius value -400..1200 (input sanity check, **not** a thermal-health rating), and monotonic timestamps.
4. Rust retains no more than 64 samples in process memory and returns the sample count and the difference in battery percentage points and temperature relative to the first retained sample.
5. Kotlin displays Rust results, or a clear error if JNI is missing, input invalid or no reading available.
6. A GitHub Actions job runs pure Rust tests, cross-compiles .so files for arm64-v8a and x86_64, builds an APK and verifies both binaries are actually packaged.

## JNI ABI
Kotlin: `RustEngine.recordBatterySample(Int, Int, Long): Long`
Rust symbol: `Java_dev_lolokeksu_legioncontrol_RustEngine_recordBatterySample`
Packed long: `count<<32 | (signed_temp_delta_as_u16)<<16 | signed_battery_delta_as_u16`.
Native returns `-1` for invalid sensor input, including out-of-order timestamps. The Rust library uses only primitive JNI types and does not dereference JNI pointers or access hidden APIs.

## Privacy / safety
No server, network requests, Root, Shizuku, Motorola Binder calls, thermal writes, boot receiver or telemetry. Data is processed on device. Rust's 64-sample computational window is *ephemeral* and resets with the process; since alpha.4, separate Kotlin SQLite data remains in private storage after restarts. These are individual user-triggered samples, **not** continuous monitoring or a battery health diagnosis.

## Build locally
Requires Rust stable + `aarch64-linux-android` / `x86_64-linux-android` targets, Android NDK 28.2.13676358, SDK 36, JDK 17, Gradle 9.6.0 (setup-gradle in CI). Build on Linux or macOS:

```sh
bash scripts/build-native.sh
gradle --no-daemon assembleDebug lintDebug
python3 scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

CI debug signing is not stable across different runners. An owned release keystore is necessary for upgradable public release APKs; no private signing keys are committed.

## Later, after backend verification
Compose UI and navigation, improved state management, installed-game discovery and GameTime read-only adapter. Do not transfer stale Java/UI or unsupported OEM switches from `Lenovo-Y70`.


## Added in alpha.2 — Rust window statistics

- JNI method: `RustEngine.readBatteryWindowStats(): Long`.
- Rust symbol: `Java_dev_lolokeksu_legioncontrol_RustEngine_readBatteryWindowStats`.
- The query is **read-only**, uses exactly the retained 1–64 samples, and returns `-1` for empty history or an unavailable/poisoned lock.
- Bits: `[count:u16 @48][min_temperature_deci_c:i16 @32][max_temperature_deci_c:i16 @16][mean_temperature_deci_c:i16 @0]`, with integer mean truncation toward zero.
- Values represent sampled Android **battery** temperature, not CPU/GPU temperature, device thermal status, sensor latency or continuous history. No sampling without an explicit user request.

## Permanent release signing

See [RELEASE-SIGNING.md](RELEASE-SIGNING.md): a fixed PKCS12 key, certificate pin and manual GitHub Actions release workflow. Four GitHub secrets must first be configured by the repository owner. Alpha.1 debug-signed APK cannot be upgraded directly to release-signed alpha.2 without an Android signature mismatch.


## Alpha.3: power snapshot, native session duration and explicit reset

- Rust JNI `RustEngine.readBatterySessionDuration(): Long`: exact `newest.elapsedRealtime - oldest.elapsedRealtime` in milliseconds among retained 64 user-triggered samples; returns `-1` for no samples/unavailable native state. Not true uptime, monitoring time, FPS, or battery life estimate.
- Rust JNI `RustEngine.resetBatterySession(): Int`: number of discarded in-memory samples; `-1` on lock failure. The UI requires explicit confirmation first, and the next new sample begins a new session. No persistent data is erased, no Android settings changed.
- Kotlin `AndroidPowerSource.read(Context, Intent?)`: Android battery broadcast reports charging status, plugged source and voltage; `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` provides current where supported, and `PowerManager.isPowerSaveMode` gives power saver state. Every unavailable value remains explicitly unknown. Current sign is manufacturer-specific and **must not** be interpreted as a Bypass switch or measured charging efficiency.
- Share report: explicit Android `ACTION_SEND`/system chooser for the last successful on-demand snapshot. No background collection, analytics or network access by the app; recipients are chosen by the user. Reset invalidates prior shareable snapshot until next measurement.
- No new permissions, no JNI pointer dereference, and no hidden Motorola APIs.

## Alpha.4: persistent diagnostic history and sensor discovery

Kotlin uses a private `SQLiteOpenHelper` database, `legion-control-history.db`, with `sessions` and `samples`. A successful Rust sample is recorded transactionally. Duplicate `session_id + elapsed_ms` samples are ignored, missing Android fields remain SQL NULL, and no external-storage permission is requested. There is **no auto-pruning** or destructive upgrade fallback; database schema changes require explicit lossless migrations. Each app process owns a native session through `ActiveSession`, so Rust monotonic timestamps are not mixed across device boots/process restarts.

Android `ACTION_CREATE_DOCUMENT` invokes the user-controlled Storage Access Framework to stream all rows as CSV (UTF-8 with BOM, CRLF, fully quoted values and empty cells for missing readings). No background exports or remote recipients. `SensorManager.getDefaultSensor` reports discoverability only and is not a live sensor recording. Battery temperatures and CPU/GPU temperatures are distinct. All new checks remain public API/read-only.

Source-of-truth: [HISTORY.md](HISTORY.md) and `scripts/check_persistent_history.py`. JVM regressions: `gradle testDebugUnitTest`; physical Android SQLite/SAF behavior requires separate device validation.


## Public API capability probe (backend-only, pending device acceptance)

`DeviceCapabilitiesBackend.read(context)` collects a snapshot on explicit invocation. It uses only `PowerManager` and `DisplayManager` public APIs, requires **no permissions, Shizuku or Root**, and does not alter device settings or start monitoring.

- `thermalStatus`: Android's reported `getCurrentThermalStatus()` code (0–6), or `null` when unavailable/invalid. This is **not** a CPU/GPU temperature, a throttle percentage, or a guarantee that the OEM exposes every severity transition.
- `sustainedPerformanceSupported`: Android's read-only `isSustainedPerformanceModeSupported()`, or `null` on failure. This does not enable sustained-performance mode.
- `activeDisplayMode` and `supportedDisplayModes`: modes reported by the primary Android display, with mode ID, physical dimensions and nominal refresh rate. These are **not** measured app FPS, touch sampling rate, or permission to change the display mode. The reported supported modes can be affected by Android version and OEM filtering.
- Invalid or unavailable OEM values stay unknown/absent; no synthetic fallback is inserted. The pure `CapabilitySanitizer` rejects malformed thermal codes, mode IDs, dimensions and refresh rates and deduplicates reported mode IDs.

The capability probe is **not connected to `MainActivity`**: UI and manual diagnostic flow remain unchanged by owner request. Only the additive Kotlin backend API and its unit tests are in scope. `testDebugUnitTest` runs `CapabilitySnapshotTest`; physical Y70 testing must separately call `DeviceCapabilitiesBackend.read(context)` and inspect the result. No certificate, package ID, versionCode, SQLite schema, CSV format, or Rust ABI changes.
