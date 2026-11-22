"""Fast static contract check before costly Kotlin/Rust Android compilation."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
android = root / "app/src/main"
manifest = ET.parse(android / "AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
assert set(x.attrib["{http://schemas.android.com/apk/res/android}name"] for x in manifest.findall("uses-permission")) == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.WRITE_SETTINGS","android.permission.VIBRATE"
}, "Backend alpha needs no extra permissions"

build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
assert 'applicationId = "dev.lolokeksu.legioncontrol"' in build
assert 'namespace = "dev.lolokeksu.legioncontrol"' in build
assert 'targetSdk = 36' in build
assert 'compileSdk = 36' in build

src = android / "java/dev/lolokeksu/legioncontrol"
kotlin = (src / "RustEngine.kt").read_text(encoding="utf-8")
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
rust = (root / "rust-core/src/lib.rs").read_text(encoding="utf-8")
assert "System.loadLibrary(\"legion_core\")" in kotlin
assert "private external fun recordBatterySample(" in kotlin
assert "Java_dev_lolokeksu_legioncontrol_RustEngine_recordBatterySample" in rust
assert "val elapsedMs = SystemClock.elapsedRealtime()" in activity
assert "engine.record(percent, temp, elapsedMs)" in activity
assert "ACTION_BATTERY_CHANGED" in activity
assert "DisplayManager" in activity
assert "HISTORY_LIMIT: usize = 64" in rust
# Second JNI contract: read-only Rust statistics, not Kotlin-calculated mock values.
assert "private external fun readBatteryWindowStats(): Long" in kotlin
assert "Java_dev_lolokeksu_legioncontrol_RustEngine_readBatteryWindowStats" in rust
assert "pub fn statistics(&self)" in rust
assert "engine.statistics()" in activity
assert "minTemperatureDeciC" in kotlin
assert "maxTemperatureDeciC" in kotlin
assert "meanTemperatureDeciC" in kotlin
assert "JNIEnv" not in rust and "std::ffi::c_void" in rust
for banned in (
    "pointer_speed", "moto_extend_service", "Runtime.getRuntime",
    "setprop ", "pm grant", "WRITE_SECURE_SETTINGS", "QUERY_ALL_PACKAGES",
):
    assert banned not in kotlin + activity + rust, banned

workflow = (root / ".github/workflows/android.yml").read_text(encoding="utf-8")
assert "bash scripts/build-native.sh" in workflow
assert "gradle --no-daemon assembleDebug lintDebug" in workflow
assert "scripts/verify_apk.py" in workflow
print("PASS: Kotlin <-> Rust JNI, backend reads, no privileges, CI integration")
