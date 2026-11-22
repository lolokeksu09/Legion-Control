#!/usr/bin/env python3
"""Capture Studio alpha25: public-API capture contract, consent/FGS order and owned MediaStore."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
activity = (src / "CaptureStudioActivity.kt").read_text(encoding="utf-8")
reader = (src / "CaptureStudioRepository.kt").read_text(encoding="utf-8")
vm = (src / "CaptureStudioViewModel.kt").read_text(encoding="utf-8")
service = (src / "CaptureRecordingService.kt").read_text(encoding="utf-8")
store = (src / "CaptureClipLibrary.kt").read_text(encoding="utf-8")
config = (src / "CaptureConfig.kt").read_text(encoding="utf-8")
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
legacy = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
unit = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/CaptureConfigTest.kt").read_text(encoding="utf-8")
assert 'versionCode = 26' in gradle and 'versionName = "0.1.0-alpha.26"' in gradle
assert 'applicationId = "dev.lolokeksu.legioncontrol"' in gradle
assert "HapticLabSchema.VERSION" in legacy, "Existing SQLite v12 must remain intact"
assert "CAPTURE STUDIO" in main and "CaptureStudioActivity::class.java" in main

permissions = {n.get(ns+"name") for n in manifest.findall("uses-permission")}
expected = {
    "android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE","android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS","android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.ACCESS_NOTIFICATION_POLICY","android.permission.WRITE_SETTINGS",
    "android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW",
}
assert permissions == expected, (permissions, expected)
for forbidden in (
    "android.permission.RECORD_AUDIO", "android.permission.READ_MEDIA_VIDEO",
    "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.BIND_ACCESSIBILITY_SERVICE"
):
    assert forbidden not in permissions
assert not manifest.findall("./application/receiver")
services = manifest.findall("./application/service")
assert services[0].get(ns+"name") == ".GameSessionRecorderService", "Preserve previous service entry"
capture_services = [s for s in services if s.get(ns+"name")==".CaptureRecordingService"]
assert len(capture_services)==1
assert capture_services[0].get(ns+"exported")=="false"
assert capture_services[0].get(ns+"foregroundServiceType")=="mediaProjection"
acts = [a for a in manifest.findall("./application/activity")
    if a.get(ns+"name")==".CaptureStudioActivity"]
assert len(acts)==1 and acts[0].get(ns+"exported")=="false"

assert "createScreenCaptureIntent()" in activity
assert "onActivityResult(" in activity
assert "if (resultCode != RESULT_OK || data == null)" in activity
assert activity.index("createScreenCaptureIntent()") < activity.index("onActivityResult(")
assert "startForegroundService(intent)" in activity
assert "CaptureConfig.fromOptions(" in reader
assert "CaptureClipLibrary.listOwn(" in reader
assert "viewModel.refresh()" in activity and "viewModel.library.observe(this)" in activity
assert "ACTION_VIEW" in activity and "ACTION_SEND" in activity
assert "FLAG_GRANT_READ_URI_PERMISSION" in activity
assert "confirmDelete(clip)" in activity and "setPositiveButton" in activity
assert "CaptureRecordingService.stop(this)" in activity
assert "getLaunchIntentForPackage" in activity

assert "FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION" in service
assert "startForeground(NOTICE, notice" in service
assert "getMediaProjection(Activity.RESULT_OK, consent)" in service
assert service.index("promoteToForeground()\n") < service.index("beginRecording(consent, config)")
assert service.index("val capture = requireNotNull(manager.getMediaProjection") < service.index("capture.registerCallback(callback, handler)")
assert service.index("capture.registerCallback(callback, handler)") < service.index("capture.createVirtualDisplay(")
assert service.count("capture.createVirtualDisplay(")==1
assert "override fun onStop()" in service
assert "finish(" in service and "onDestroy()" in service
assert "START_NOT_STICKY" in service
assert "handler.postDelayed(autoStop, config.maxDurationMs)" in service
assert "handler.removeCallbacks(autoStop)" in service
assert "mediaRecorder.start()" in service
assert "recorder?.stop()" in service
assert "MediaRecorder.VideoSource.SURFACE" in service
assert "MediaRecorder.VideoEncoder.H264" in service
assert "MediaRecorder.OutputFormat.MPEG_4" in service
assert "CaptureClipLibrary.createPending(this" in service
assert "CaptureClipLibrary.publish(this" in service
assert "CaptureClipLibrary.delete(this" in service
assert "CaptureClipLibrary.delete(app, clip.uri)" in reader
assert "repository.deleteOwnClip(clip)" in vm
assert "stopForeground(STOP_FOREGROUND_REMOVE)" in service
assert "stopSelf()" in service and "recorderStarted && elapsed >= 1000" in service
assert "registerReceiver(" not in service and "BOOT_COMPLETED" not in service
assert "AudioSource" not in service and "getAudioRecord" not in service
assert "MediaProjection" not in (src / "GameToolboxService.kt").read_text(encoding="utf-8")

assert "OWNER_PACKAGE_NAME" in store and "RELATIVE_PATH" in store
assert "IS_PENDING" in store and "createPending" in store and "publish" in store
assert 'const val RELATIVE_DIR = "Movies/LegionControl/"' in store
assert "contentResolver.delete(uri, null, null)" in store
assert "context.packageName, RELATIVE_DIR" in store
assert "video/mp4" in store
assert "CaptureConfigTest" in unit and unit.count("@Test") >= 6
assert "scaledSize" in config and "require(screenWidth > 0 && screenHeight > 0)" in config
assert "setOf(1280, 1920)" in config and "setOf(1, 5, 15)" in config
print("PASS alpha25: one-time consent, correct mediaProjection FGS order, bounded H264, own pending MediaStore and manual clip lifecycle")
