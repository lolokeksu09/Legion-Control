#!/usr/bin/env python3
"""alpha.27: verify actual MVVM backends, preserve Rust writes and screen recording consent."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
def read(name): return (src/name).read_text(encoding="utf-8")

main=read("MainActivity.kt")
controller=read("RustSampleController.kt")
diagnostic_repo=read("DiagnosticsHistoryRepository.kt")
diagnostic_vm=read("DiagnosticsHistoryViewModel.kt")
capture=read("CaptureStudioActivity.kt")
capture_repo=read("CaptureStudioRepository.kt")
capture_vm=read("CaptureStudioViewModel.kt")
capture_service=read("CaptureRecordingService.kt")
library=read("CaptureClipLibrary.kt")
preset=read("CapturePresetRules.kt")
unit=(root/"app/src/test/java/dev/lolokeksu/legioncontrol/BackendRefactorRulesTest.kt").read_text()
gradle=(root/"app/build.gradle.kts").read_text()
manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
assert 'versionCode = 43' in gradle and 'versionName = "0.1.0-alpha.43"' in gradle
assert 'applicationId = "dev.lolokeksu.legioncontrol"' in gradle
assert "HapticLabSchema.VERSION" in read("LocalHistoryStore.kt")
assert "private external fun recordBatterySample(" in read("RustEngine.kt")
assert "HISTORY_LIMIT: usize = 64" in (root/"rust-core/src/lib.rs").read_text()

assert "class MainActivity : ComponentActivity()" in main
assert "ViewModelProvider(this)[DiagnosticsHistoryViewModel::class.java]" in main
assert "historyModel.report.observe(this)" in main
assert "diagnosticsExecutor.execute { historyModel.request(DiagnosticsReadKind.HISTORY) }" in main
for kind in ("HISTORY","SENSOR_QUALITY","SESSION_PROFILE","THERMAL","TIMED_BATTERY","SENSOR_READINGS"):
    assert "DiagnosticsReadKind."+kind in main
assert "private fun sample(): TimedBatteryPoint?" in main
assert "sampleController.sample()" in main
assert "fun sample(): TimedBatteryPoint?" in controller
assert "engine.record(percent, temp, elapsedMs)" in controller
assert "engine.statistics()" in controller
assert "history.record(StoredReading(" in controller
assert "history.recordDiagnostic(" in controller
assert controller.index("history.record(StoredReading(") < controller.index("history.recordDiagnostic(")
assert "history.recordSensorCapture(outcome)" in main
assert "history.recordTimedBatteryTest(completed)" in main
assert "history.writeDiagnosticArchive(it)" in main
assert "writer.shutdown()" in controller and "history.close()" in controller
assert "ViewModelProvider(this)[RustSampleController::class.java]" in main
assert "historyModel.invalidate(\"sensors\")" in main

assert "class DiagnosticsHistoryRepository(" in diagnostic_repo
for signature in ("history.recentSessions()", "history.recentTimedBatteryTests()",
                  "history.recentSensorAttempts()", "history.latestSessionReadings()",
                  "history.recentThermalReadings()", "history.lastSensorSamples()"):
    assert signature in diagnostic_repo, signature
assert "RussianQuantity.measurements(total.toInt())" in diagnostic_repo
assert "SessionAnalytics.analyze(points)" in diagnostic_repo
assert "SensorQualityAnalytics.format(" in diagnostic_repo
assert "ThermalHistoryReport.format(" in diagnostic_repo
assert "fun read(kind: DiagnosticsReadKind)" in diagnostic_repo
assert "history.close()" in diagnostic_repo

assert "AndroidViewModel" in diagnostic_vm and "LiveData" in diagnostic_vm
assert "Executors.newSingleThreadExecutor()" in diagnostic_vm
assert "AtomicIntegerArray(5)" in diagnostic_vm
assert "mainHandler.post {" in diagnostic_vm
assert "generations.get(index) == generation" in diagnostic_vm
assert "worker.execute { runCatching { repository.close() } }" in diagnostic_vm
assert "cleared.set(true)" in diagnostic_vm

assert "class CaptureStudioActivity : ComponentActivity()" in capture
assert "ViewModelProvider(this)[CaptureStudioViewModel::class.java]" in capture
assert "viewModel.library.observe(this)" in capture
assert "viewModel.refresh()" in capture
assert "viewModel.deleteConfirmed(clip)" in capture
assert "viewModel.setOption(name, values[index])" in capture
assert "CaptureClipLibrary.delete(" not in capture
assert "CaptureClipLibrary.listOwn(" not in capture
assert "Executors.newSingleThreadExecutor()" not in capture
assert "MediaProjectionManager" in capture and "createScreenCaptureIntent()" in capture
assert "startForegroundService(intent)" in capture
assert "CaptureRecordingService.stop(this)" in capture
assert "confirmDelete(clip)" in capture and "setPositiveButton" in capture
assert "Intent.ACTION_VIEW" in capture and "Intent.ACTION_SEND" in capture

assert "class CaptureStudioRepository(" in capture_repo
assert "CapturePresetRules.requireSupported(name, value)" in capture_repo
assert "CaptureClipLibrary.listOwn(app, page)" in capture_repo
assert "CaptureClipLibrary.delete(app, clip.uri)" in capture_repo
assert "CaptureClipLibrary.isOwnPublished(app, clip.uri)" in capture_repo
assert "getSharedPreferences(CaptureRecordingService.PREFS" in capture_repo
assert "class CaptureStudioViewModel(" in capture_vm
assert "Executors.newSingleThreadExecutor()" in capture_vm
assert "repository.deleteOwnClip(clip)" in capture_vm
assert "revision.incrementAndGet()" in capture_vm
assert "onCleared()" in capture_vm
for forbidden_api in ("getMediaProjection(", "createScreenCaptureIntent(", "MediaProjectionManager", "startForegroundService("):
    assert forbidden_api not in capture_vm + capture_repo
assert "private val supported = mapOf(" in preset
assert "CapturePresetRules" in unit and unit.count("@Test") >= 3

assert "getMediaProjection(Activity.RESULT_OK, consent)" in capture_service
assert "capture.registerCallback(callback, handler)" in capture_service
assert "CaptureClipLibrary.createPending(this" in capture_service
assert "CaptureClipLibrary.publish(this" in capture_service
assert "MediaStore.MediaColumns.IS_PENDING" in library

permissions={x.get(ns+"name") for x in manifest.findall("uses-permission")}
assert permissions == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS", "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY", "android.permission.WRITE_SETTINGS",
    "android.permission.VIBRATE", "android.permission.SYSTEM_ALERT_WINDOW",
}
assert manifest.find("./application/service").get(ns+"name")==".GameSessionRecorderService"
print("PASS alpha27: real lifecycle read models, unchanged Rust/SQLite writer, one-time screen consent and app-owned clip operations")
