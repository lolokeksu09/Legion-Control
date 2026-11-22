"""Alpha.6 read-only thermal/battery delta contract and existing-screen integration."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
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
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.WRITE_SETTINGS"
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
assert "diagnosticTrends = DiagnosticTrendEngine()" in activity
assert "diagnosticTrends.record(current, percent)" in activity
assert "diagnosticTrends.clear()" in activity
assert "DiagnosticTrendTextReport.format(trend)" in activity
assert "latestReport = reportHeader?.plus(complete)" in activity
assert "runOnUiThread {" in activity
assert "!isDestroyed && requestId == diagnosticRequestId" in activity
assert activity.count("layout.addView(Button(this).apply {") == 22
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
assert "versionCode = 22" in gradle
assert 'versionName = "0.1.0-alpha.22"' in gradle
for unsafe in ("Shizuku.getInstance", "WRITE_SECURE_SETTINGS", "setprop ", "pm grant",
               "QUERY_ALL_PACKAGES", "exec(", "getDeclaredMethod("):
    assert unsafe not in activity + engine + current + text_report, unsafe
print("PASS: alpha.6 trend engine, real app wiring, no privileges or UI redesign")

assert 'text = "Снять показания датчиков"' in activity
assert 'text = "Последние показания датчиков"' in activity
