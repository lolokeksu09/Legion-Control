"""Contract for alpha.5: diagnostic backend is reachable from existing MainActivity."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
report = (src / "DiagnosticsTextReport.kt").read_text(encoding="utf-8")
combined = (src / "DeviceDiagnosticsBackend.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/DiagnosticsTextReportTest.kt").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
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
}, "No privileges needed"
assert activity.count("layout.addView(Button(this).apply {") == 19, "Keep all diagnostic actions"
assert "diagnosticsExecutor = Executors.newSingleThreadExecutor()" in activity
assert "diagnosticsExecutor.execute {" in activity
assert "DeviceDiagnosticsBackend.read(applicationContext)" in activity
assert "DiagnosticsTextReport.format(" in activity
assert "runOnUiThread {" in activity
assert "!isDestroyed && requestId == diagnosticRequestId" in activity
assert "diagnosticRequestId++" in activity and "diagnosticsExecutor.shutdown()" in activity
assert "latestReport = reportHeader?.plus(complete)" in activity
assert 'text = "Обновить измерение"' in activity and "setOnClickListener { manualSample() }" in activity
assert 'format(snapshot: DeviceDiagnosticSnapshot): String' in report
assert "ProbeState.UNAVAILABLE" in report
assert "ProbeState.ERROR" in report
assert "ProbeState.RATE_LIMITED" in report
assert "DeviceCapabilitiesBackend.read(context)" in combined
assert "ResourceTelemetryBackend.read(context)" in combined
assert "fun successfulReadingsExposeSourcesAndActualUnits()" in tests
assert "fun unavailableAndErrorsAreNotRenderedAsZero()" in tests
assert 'versionCode = 19' in build
assert 'versionName = "0.1.0-alpha.19"' in build
print("PASS: manual MainActivity wiring, off-UI probes, honest reports, no UI redesign")

assert 'text = "Снять показания датчиков"' in activity
assert 'text = "Последние показания датчиков"' in activity
