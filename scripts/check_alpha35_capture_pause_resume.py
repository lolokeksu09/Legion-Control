#!/usr/bin/env python3
"""Alpha.35: explicit, bounded MediaRecorder pause/resume; never passive capture."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
read=lambda name: (src/name).read_text(encoding="utf-8")
service=read("CaptureRecordingService.kt")
clock=read("CaptureRecordingClock.kt")
ui=read("CaptureStudioActivity.kt")
repo=read("CaptureStudioRepository.kt")
screenshot=read("CaptureScreenshotService.kt")
unit=(root/"app/src/test/java/dev/lolokeksu/legioncontrol/CaptureRecordingClockTest.kt").read_text()
manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
permissions={x.get(ns+"name") for x in manifest.findall("uses-permission")}

assert all(s in service for s in (
    'const val ACTION_PAUSE =', 'const val ACTION_RESUME =',
    "currentRecorder.pause()", "currentRecorder.resume()",
    "clock.pause(", "clock.resume(", "CaptureRecordingClock.MAX_PAUSE_MS",
    "handler.removeCallbacks(autoStop)", "handler.removeCallbacks(pauseTimeout)",
    "handler.postDelayed(autoStop, remaining)",
    "handler.postDelayed(pauseTimeout, CaptureRecordingClock.MAX_PAUSE_MS)",
    "captureClock?.activeElapsedMs(SystemClock.elapsedRealtime())",
    "if (recording && !stopping)", "if (intent?.action == ACTION_STOP)",
    "startForeground(NOTICE, buildNotification()",
    "getMediaProjection(Activity.RESULT_OK, consent)",
    "CaptureClipLibrary.createPending(", "CaptureClipLibrary.publish(",
    "CaptureClipLibrary.delete(", "return START_NOT_STICKY",
    "MediaRecorder.VideoSource.SURFACE", "MediaRecorder.VideoEncoder.H264",
    "handler.postDelayed(autoStop, config.maxDurationMs)"
))
assert service.count("capture.createVirtualDisplay(")==1
assert service.index("promoteToForeground()") < service.index("beginRecording(consent, config)")
assert service.index("mediaRecorder.start()") < service.index("captureClock = CaptureRecordingClock(")
assert service.index("currentRecorder.pause()") < service.index("clock.pause(")
assert service.index("currentRecorder.resume()") < service.index("clock.resume(")
assert "ACTION_PAUSE else ACTION_RESUME" not in service
assert '"Пауза" else "Продолжить"' not in service
assert 'if (paused) ACTION_RESUME else ACTION_PAUSE' in service
assert 'if (paused) "Продолжить" else "Пауза"' in service
assert "PendingIntent.getService(" in service and "NotificationManager" in service
assert "CaptureRecordingService.pause(this)" in ui
assert "CaptureRecordingService.resume(this)" in ui
assert "CaptureRecordingService.stop(this)" in ui
assert "CaptureRecordingService.paused" in repo
assert "CaptureScreenshotService.active" in service
assert "CaptureRecordingService.recording" in screenshot
for token in ("fun pause(nowMs: Long)", "fun resume(nowMs: Long)",
              "fun activeElapsedMs(nowMs: Long)", "fun remainingMs(nowMs: Long)",
              "const val MAX_PAUSE_MS = 300_000L"):
    assert token in clock, token
assert unit.count("@Test") >= 9
services=[a for a in manifest.findall("./application/service")
          if a.get(ns+"name")==".CaptureRecordingService"]
assert len(services)==1 and services[0].get(ns+"exported")=="false"
assert services[0].get(ns+"foregroundServiceType")=="mediaProjection"
for forbidden in (
                  "android.permission.WRITE_SECURE_SETTINGS",
                  "android.permission.MANAGE_EXTERNAL_STORAGE",
                  "android.permission.QUERY_ALL_PACKAGES"):
    assert forbidden not in permissions
for forbidden in ("queryUsageStats", "AccessibilityService",
                  "Runtime.getRuntime(", "java.net.", "BOOT_COMPLETED"):
    assert forbidden not in service+clock
print("PASS alpha35: explicit pause/resume, monotonic active time, paused timeout, same MP4 and no new privileges")
