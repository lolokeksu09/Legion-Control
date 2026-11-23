"""Alpha.6 read-only thermal/battery delta contract and existing-screen integration."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
controller = (src / "RustSampleController.kt").read_text(encoding="utf-8")
engine = (src / "DiagnosticTrendEngine.kt").read_text(encoding="utf-8")
text_report = (src / "DiagnosticTrendTextReport.kt").read_text(encoding="utf-8")
current = (src / "DiagnosticsTextReport.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/DiagnosticTrendEngineTest.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
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
assert "class DiagnosticTrendEngine" in engine
assert "fun record(snapshot: DeviceDiagnosticSnapshot, batteryPercent: Int?)" in engine
assert "elapsed <= 0L || elapsed > MAX_COMPARISON_INTERVAL_MS" in engine
assert "abs(counterDelta) >= MIN_CHARGE_CHANGE_MICRO_AH" in engine
assert "abs(percentDelta) >= MIN_PERCENT_CHANGE" in engine
assert "MIN_BATTERY_COMPARISON_INTERVAL_MS = 60_000L" in engine
assert "fun clear()" in engine
assert "enum class BatteryCounterAgreement" in engine
assert "object ThermalSeverity" in engine
assert "BatteryCounterAgreement.DISAGREE" in text_report
assert "не является диагностикой износа" in text_report
assert "ThermalSeverity.describe(capabilities.thermalStatus)" in current
assert "diagnosticTrends = DiagnosticTrendEngine()" in controller
assert "diagnosticTrends.record(current, percent)" in controller
assert "diagnosticTrends.clear()" in controller
assert "DiagnosticTrendTextReport.format(trend)" in controller
assert "mutableUiState.value = RustSampleUiState(complete, reportHeader?.plus(complete))" in controller
assert "mainHandler.post {" in controller
assert "!cleared && requestId == diagnosticRequestId" in controller
assert activity.count("layout.addView(Button(this).apply {") == 25
for test_name in (
    "firstReadingIsBaselineAndSecondShowsMeasuredChanges",
    "missingDataNeverBecomesAZeroDelta",
    "contradictoryCounterNeedsRepeatedValidationNotBatteryHealthClaim",
    "stableBatteryPercentageCannotProveCounterAccuracy",
    "shortIntervalsMustNotProduceStrongConsistencyJudgement",
    "monotonicClockRollbackAndHugeGapDiscardStaleBaseline",
    "clearedSessionMustNotCompareAgainstOldMeasurements",
    "thermalCodesMatchAndroidPlatformDefinitions",
):
    assert test_name in tests, test_name
assert "versionCode = 46" in gradle
assert 'versionName = "0.1.0-alpha.46"' in gradle
for unsafe in ("Shizuku.getInstance", "WRITE_SECURE_SETTINGS", "setprop ", "pm grant",
               "QUERY_ALL_PACKAGES", "exec(", "getDeclaredMethod("):
    assert unsafe not in activity + controller + engine + current + text_report, unsafe
print("PASS: alpha.6 trend engine, real app wiring, no privileges or UI redesign")

assert 'text = "Снять показания датчиков"' in activity
assert 'text = "Последние показания датчиков"' in activity
