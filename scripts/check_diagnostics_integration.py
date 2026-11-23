"""Contract for alpha.5: diagnostic backend is reachable from existing MainActivity."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
controller = (src / "RustSampleController.kt").read_text(encoding="utf-8")
report = (src / "DiagnosticsTextReport.kt").read_text(encoding="utf-8")
combined = (src / "DeviceDiagnosticsBackend.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/DiagnosticsTextReportTest.kt").read_text(encoding="utf-8")
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
}, "No privileges needed"
assert activity.count("layout.addView(Button(this).apply {") == 25, "Keep all diagnostic actions"
assert "val writer = Executors.newSingleThreadExecutor()" in controller
assert "diagnosticsExecutor.execute {" in activity
assert "writer.execute {" in controller
assert "DeviceDiagnosticsBackend.read(context)" in controller
assert "DiagnosticsTextReport.format(" in controller
assert "mainHandler.post {" in controller
assert "!cleared && requestId == diagnosticRequestId" in controller
assert "diagnosticRequestId++" in controller and "writer.shutdown()" in controller
assert "mutableUiState.value = RustSampleUiState(complete, reportHeader?.plus(complete))" in controller
assert "sampleController.uiState.observe(this)" in activity
assert 'text = "Обновить измерение"' in activity and "setOnClickListener { manualSample() }" in activity
assert 'format(snapshot: DeviceDiagnosticSnapshot): String' in report
assert "ProbeState.UNAVAILABLE" in report
assert "ProbeState.ERROR" in report
assert "ProbeState.RATE_LIMITED" in report
assert "DeviceCapabilitiesBackend.read(context)" in combined
assert "ResourceTelemetryBackend.read(context)" in combined
assert "fun successfulReadingsExposeSourcesAndActualUnits()" in tests
assert "fun unavailableAndErrorsAreNotRenderedAsZero()" in tests
assert 'versionCode = 40' in build
assert 'versionName = "0.1.0-alpha.40"' in build
print("PASS: manual MainActivity wiring, off-UI probes, honest reports, no UI redesign")

assert 'text = "Снять показания датчиков"' in activity
assert 'text = "Последние показания датчиков"' in activity
