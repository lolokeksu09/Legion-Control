#!/usr/bin/env python3
"""Strict screenshot implementation contract, not proof of physical Android functionality."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
read = lambda name: (src / name).read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
perms = {x.get(ns+"name") for x in manifest.findall("uses-permission")}
assert "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" in perms
for forbidden in ("android.permission.READ_MEDIA_IMAGES",
                  "android.permission.READ_MEDIA_VIDEO", "android.permission.MANAGE_EXTERNAL_STORAGE",
                  "android.permission.QUERY_ALL_PACKAGES", "android.permission.WRITE_SECURE_SETTINGS"):
    assert forbidden not in perms
s = [x for x in manifest.findall("./application/service")
     if x.get(ns+"name") == ".CaptureScreenshotService"]
assert len(s) == 1
assert s[0].get(ns+"exported") == "false"
assert s[0].get(ns+"foregroundServiceType") == "mediaProjection"

service = read("CaptureScreenshotService.kt")
library = read("CaptureScreenshotLibrary.kt")
policy = read("CaptureScreenshotPolicy.kt")
activity = read("CaptureStudioActivity.kt")
repo = read("CaptureStudioRepository.kt")
vm = read("CaptureStudioViewModel.kt")
video = read("CaptureRecordingService.kt")
unit = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/CaptureScreenshotPolicyTest.kt").read_text()
assert "startForeground(NOTICE, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)" in service
assert service.index("promote()") < service.index("begin(consent)")
assert service.index("capture.registerCallback(callback, main)") < service.index("capture.createVirtualDisplay(")
assert service.count("capture.createVirtualDisplay(") == 1
assert "manager.getMediaProjection(Activity.RESULT_OK, consent)" in service
assert "CaptureScreenshotPolicy.dimensions(" in service
assert "CaptureScreenshotPolicy.paddedWidth(" in service
assert "image.use" in service
assert "Bitmap.CompressFormat.PNG" in service
assert "CaptureScreenshotLibrary.createPending(" in service
assert "CaptureScreenshotLibrary.publish(" in service
assert "CaptureScreenshotLibrary.delete(" in service
assert "stopForeground(STOP_FOREGROUND_REMOVE)" in service
assert "stopSelf()" in service and "override fun onStop()" in service
assert "quitSafely()" in service and "capture?.stop()" in service
assert "START_NOT_STICKY" in service
assert "CaptureRecordingService.recording" in service
assert "CaptureScreenshotService.active" in video
assert "MediaProjectionManager" in activity and "createScreenCaptureIntent()" in activity
assert "CaptureScreenshotService.ACTION_START" in activity
assert "startForegroundService(screenshot)" in activity
assert "CaptureScreenshotService.cancel(this)" in activity
assert "viewModel.refreshScreenshots()" in activity
assert "viewModel.deleteConfirmedScreenshot(shot)" in activity
assert "CaptureScreenshotLibrary.listOwn(app, page)" in repo
assert "CaptureScreenshotLibrary.isOwnPublished(app, screenshot.uri)" in repo
assert "repository.deleteOwnScreenshot(shot)" in vm
assert "Pictures/LegionControl/" in library
assert "OWNER_PACKAGE_NAME" in library and "IS_PENDING" in library
assert "ContentUris.withAppendedId" in library
assert "image/png" in library and "setDataAndType(shot.uri, \"image/png\")" in activity
assert "assertEquals(872 to 1920" in unit and unit.count("@Test") >= 7
assert "TIMEOUT_MS = 12_000L" in policy
assert "MAX_EDGE = 1920" in policy
for forbidden in ("java.net.", "Runtime.getRuntime", "queryUsageStats",
                  "AccessibilityService", "MediaRecorder", "AudioRecord", "BOOT_COMPLETED"):
    assert forbidden not in service
print("PASS alpha34: explicit single screenshot consent, bounded frame, own-only PNG media lifecycle")
