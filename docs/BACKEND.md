# Legion Control backend · v0.1.0-alpha.2

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
No server, network requests, Root, Shizuku, Motorola Binder calls, thermal writes, boot receiver or telemetry. Data is processed on device. Rust history is *ephemeral* and resets when the process ends. These are individual user-triggered samples, **not** continuous monitoring or a battery health diagnosis.

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
