"""Alpha.9: battery broadcast health/cycles are called from existing manual report."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
controller = (src / "RustSampleController.kt").read_text(encoding="utf-8")
backend = (src / "BatteryHealthBackend.kt").read_text(encoding="utf-8")
model = (src / "BatteryHealthSnapshot.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/BatteryHealthReportTest.kt").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()

assert set(x.attrib["{http://schemas.android.com/apk/res/android}name"] for x in manifest.findall("uses-permission")) == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
}
assert "BatteryManager.EXTRA_HEALTH" in backend
assert "BatteryManager.EXTRA_CYCLE_COUNT" in backend
assert "BatteryManager.EXTRA_CHARGING_STATUS" in backend
assert "BatteryManager.EXTRA_TECHNOLOGY" in backend
assert "BatteryManager.EXTRA_PRESENT" in backend
assert "BatteryManager.EXTRA_BATTERY_LOW" in backend
assert "sdk >= 34" in backend
assert "it.hasExtra(key)" in backend
assert "BatteryHealthSanitizer" in backend and "BatteryHealthSnapshot(" in backend
assert "fun batteryPowerWatts(voltageMilliVolts: Int?, currentMicroAmps: Int?)" in model
assert "val ua = currentMicroAmps?.takeIf" in model
assert "return mv.toDouble() * ua.toDouble() / 1_000_000_000.0" in model
assert "GOOD — не процент износа" in model
assert "не подтверждает мощность адаптера или наличие Bypass" in model
assert "val batteryHealth = BatteryHealthBackend.read(battery)" in controller
assert "BatteryHealthReport.format(" in controller
assert "val snapshot = output.toString()" in controller
assert controller.index("BatteryHealthReport.format(") < controller.index("val snapshot = output.toString()")
assert "reportHeader?.plus(snapshot)" in controller
assert "reportHeader?.plus(complete)" in controller
assert activity.count("layout.addView(Button(this).apply {") == 25, "All existing battery controls remain"
for test in (
    "androidHealthIsNotDegradationPercentage",
    "missingValuesDoNotBecomeZeroOrFalse",
    "sanitizersRejectInvalidOemValues",
    "terminalElectricalPowerIsEstimateNotChargerWattage",
):
    assert "fun " + test + "()" in tests
for forbidden in ("Shizuku", "WRITE_SECURE_SETTINGS", "setprop ",
                  "QUERY_ALL_PACKAGES", "Runtime.getRuntime().exec",
                  "registerReceiver", "getDeclaredMethod("):
    assert forbidden not in backend + model, forbidden
assert 'versionCode = 44' in build
assert 'versionName = "0.1.0-alpha.44"' in build
print("PASS: public Android battery health/cycles, honest terminal watts, existing report/share, no Shizuku/UI")

assert 'text = "Снять показания датчиков"' in activity
assert 'text = "Последние показания датчиков"' in activity
