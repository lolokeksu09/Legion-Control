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

In alpha.5 the capability probe is connected to the existing manual `MainActivity.sample()` action. Existing layout and buttons remain unchanged; the snapshot is read via the aggregated backend off the UI thread and appended to the existing report. `testDebugUnitTest` runs `CapabilitySnapshotTest`; physical Y70 testing must separately call `DeviceCapabilitiesBackend.read(context)` and inspect the result. No certificate, package ID, versionCode, SQLite schema, CSV format, or Rust ABI changes.


## Resource telemetry backend (stacked on public capability probe; no UI changes)

The additive `ResourceTelemetryBackend.read(context)` performs an **explicit, synchronous, read-only** public-API snapshot. It does not sample in the background, add Android permissions, write hardware settings, invoke Shizuku or Root, or modify local SQLite/CSV. The caller must invoke it off the UI thread when integrating it in a future app layer. In alpha.5 it is connected to the existing manual sample action without changing the UI layout, and its read occurs on a dedicated executor.

The public, typed `DeviceDiagnosticsBackend.read(context)` combines the previous `DeviceCapabilitiesBackend` with this new resource snapshot. It is a library entry point, **not** proof that the Lenovo Y70 has reported these particular values.

### Measurements and scope

| Field | Android source | Interpret carefully |
| --- | --- | --- |
| `systemMemory` | `ActivityManager.getMemoryInfo` | Kernel-reported total/available RAM and Android low-memory threshold; not game-specific RAM consumption |
| `appFilesystem` | `StatFs(context.filesDir.absolutePath)` | Available and total bytes on app-private filesystem; not every storage partition or SD card |
| `onlineProcessorCount` | `Runtime.availableProcessors()` | Process-visible logical processors, **not** guaranteed physical SoC core count |
| `batteryChargeCounterMicroAh` | `BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER` | OEM-reported remaining battery charge in µAh; not rated/full design capacity or health |
| `batteryCurrentNowMicroAmps` | `BATTERY_PROPERTY_CURRENT_NOW` | Signed instantaneous current in µA; no Bypass/wattage claims |
| `batteryCurrentAverageMicroAmps` | `BATTERY_PROPERTY_CURRENT_AVERAGE` | Signed OEM-reported averaged current; averaging window can vary |
| `batteryEnergyCounterNanoWh` | `BATTERY_PROPERTY_ENERGY_COUNTER` | Reported remaining energy in nWh; no fabricated value when unavailable |
| `thermalHeadroom` | `PowerManager.getThermalHeadroom(0)` | Unitless Android headroom. 1.0 denotes severe-throttling threshold, **not 100% CPU/GPU utilization or battery temperature** |

The `thermalHeadroom` query has a **1500 ms process-local minimum interval** because Android advises against calling it more often than about once per second. A too-early request returns `RATE_LIMITED` with no value; a genuine Android `NaN`/invalid result stays `UNAVAILABLE`. The gate also recovers from monotonic-clock resets after reboot. This is not an automatic background loop.

### Explicit field states

Each field has a `ProbeResult<T>` with `state` and nullable `value`:
- `AVAILABLE`: validated value reported by the source
- `UNAVAILABLE`: service absent or value missing/invalid
- `ERROR`: Android getter raised an exception
- `RATE_LIMITED`: our own thermal gate intentionally skipped the platform query

A value is present **only** in `AVAILABLE`. `0` is retained where the Android property legitimately reports zero; it is never substituted for an absent value. Counter readings do not establish battery wear, battery capacity, FPS, or thermal throttle percentage. The platform can expose incomplete or OEM-filtered information.

### Verification and acceptance

- Pure JVM tests: `ResourceTelemetryTest` and `ThermalHeadroomGateTest`; they include invalid values, missing sentinel codes, platform exceptions, repeated calls and clock resets.
- Source contract: `python3 scripts/check_resource_telemetry.py`, executed in Android CI and release verification alongside the established tests.
- PR CI must compile and test the changed Android app with JNI plus existing Rust tests. **Physical-device verification remains pending**: call `DeviceDiagnosticsBackend.read(context)` on the Lenovo Y70, inspect each `ProbeState`, inspect battery current sign while charging/discharging and verify that rapid thermal requests return `RATE_LIMITED`. No UI controls, no privilege requests and no device-state changes are introduced.


## Alpha.5 — backend integration into the existing diagnostic screen

- User instruction: every subsequent backend addition must be integrated into the app's actual user-triggered execution path, **not just included in the APK**, and delivered to `main` only after verification. Keep frontend/design work out of scope.
- The existing `Обновить измерение` button still reads battery through Rust and persists its former snapshot to SQLite. It also schedules one `DeviceDiagnosticsBackend.read(applicationContext)` off the main thread.
- The resulting `DiagnosticsTextReport.format()` output is appended to the existing text report and to the already-existing share flow. No additional buttons, layouts, permissions, background polling or external network calls.
- On newer requests or an explicitly reset Rust session, stale task results are ignored by `diagnosticRequestId`; executor shutdown occurs in `onDestroy`.
- `0.1.0-alpha.5` increments `versionCode` to 5 and preserves the original application ID and pinned release signing identity. No SQLite schema, Rust ABI or data-reset change.
- `DiagnosticsTextReportTest` exercises available, unavailable, API-error and rate-limited values; `scripts/check_diagnostics_integration.py` verifies real wiring into MainActivity. Full CI verifies the APK. Physical Y70 verification must still confirm display/report/share and update compatibility before public distribution.

 
## Alpha.6 — verified direction-only trends for manually captured snapshots

The user reports a **Lenovo Legion Y70 (2026)** using a Motorola-identity firmware. The code does **not** infer the physical phone model or nominal battery capacity from `Build.MODEL` and does not hardcode any manufacturer's advertised capacity. Reported display modes are Android's platform modes and must not be treated as in-game FPS.

The existing `MainActivity.sample()` action invokes the public-API diagnostic backend on a dedicated executor, then (only for the latest accepted sample) runs the pure `DiagnosticTrendEngine.record(snapshot, batteryPercent)` on the UI thread before constructing the same existing text/share report. No extra UI controls and no automatic sampling.

- Thermal status codes 0–6 are decoded per Android `PowerManager` definitions, keeping the code for auditing. `MODERATE` does not directly quantify CPU/GPU temperature. A headroom value close to 1.0 is closer to the severe-throttling boundary but does **not** mean a percentage of utilization.
- The new report compares status, unitless thermal headroom, battery charge counter in microampere-hours and battery percentage between two **actual** readings. Null/RATE_LIMITED/ERROR signals do not become zeros.
- Only when independent battery % and `BATTERY_PROPERTY_CHARGE_COUNTER` change by meaningful amounts (at least 2 percentage points and 50,000 microampere-hours over at least 60s) does a weak **direction-agreement** check run. This is a heuristic, not proof of gauge accuracy, rated capacity or battery health. Disagreement requests another reading, not a diagnosis.
- This trend memory is bounded to one preceding accepted snapshot within the current Activity/process and is cleared with an explicit Rust session reset; Android elapsed clock rollback or a gap greater than six hours creates a new baseline.
- No new SQLite schema, saved battery health metric, Root/Shizuku, OEM writes, background timers or network access.

**Verification:** `DiagnosticTrendEngineTest` checks valid trends, absent input, disagreement, short intervals, clock rollback and reset. `scripts/check_thermal_battery_trends.py` guards the app's actual call path. Android debug CI and permanent signer pipeline run those contracts and JVM tests. A physical Y70 repeat measurement is still required to validate the displayed values and signed APK installation.

The new comparison's first accepted reading is explicitly a baseline, not a zero-delta measurement. Subsequent accepted readings are shown in the same shareable manual report; no historical backfill from the separate SQLite store is assumed. This distinction also makes stale async results harmless after reset.
