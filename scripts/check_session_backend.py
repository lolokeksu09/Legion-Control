"""Focused alpha.3 contract: Rust-managed sessions, explicit reset, local reports."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
controller = (src / "RustSampleController.kt").read_text(encoding="utf-8")
rust_api = (src / "RustEngine.kt").read_text(encoding="utf-8")
power = (src / "AndroidPowerSource.kt").read_text(encoding="utf-8")
native = (root / "rust-core/src/lib.rs").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"

assert set(x.attrib["{http://schemas.android.com/apk/res/android}name"] for x in manifest.findall("uses-permission")) == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
}, "No privileged permissions for battery telemetry"
for name in ("private external fun resetBatterySession(): Int",
             "private external fun readBatterySessionDuration(): Long",
             "fun resetSession(): Result<Int>", "fun durationMs(): Result<Long>"):
    assert name in rust_api, name
for name in ("RustEngine_resetBatterySession",
             "RustEngine_readBatterySessionDuration",
             "pub fn clear(&mut self)",
             "pub fn duration_ms(&self)"):
    assert name in native, name
assert "elapsedMs" not in power, "Android power source must not calculate synthetic Rust duration"
for name in ("BatteryManager.EXTRA_PLUGGED", "BatteryManager.EXTRA_STATUS",
             "BatteryManager.EXTRA_VOLTAGE",
             "BatteryManager.BATTERY_PROPERTY_CURRENT_NOW", "PowerManager"):
    assert name in power, name
for name in ("engine.resetSession()", "engine.durationMs()", "AndroidPowerSource.read("):
    assert name in controller, name
for name in ("sampleController.resetSession()", "Intent.ACTION_SEND", "Intent.EXTRA_TEXT",
             "Intent.createChooser", "AlertDialog.Builder(this)", "latestReport = null"):
    assert name in activity, name
assert "setPositiveButton(" in activity and "setNegativeButton(" in activity
assert "versionCode = 27" in build
assert 'versionName = "0.1.0-alpha.27"' in build
for forbidden in ("WRITE_SECURE_SETTINGS", "QUERY_ALL_PACKAGES", "setprop ",
                  "pointer_speed", "moto_extend_service", "Runtime.getRuntime"):
    assert forbidden not in power + activity + controller + rust_api + native
print("PASS: alpha.3 explicit Rust history reset/duration, public power API and user-initiated report")
