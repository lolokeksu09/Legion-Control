#!/usr/bin/env python3
"""Alpha.24 strict UI/permission safety contract. No synthetic device-success claims."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
activity = (src / "GameToolboxActivity.kt").read_text(encoding="utf-8")
service = (src / "GameToolboxService.kt").read_text(encoding="utf-8")
tuning = (src / "ToolboxTuning.kt").read_text(encoding="utf-8")
system = (src / "SmartSystemControls.kt").read_text(encoding="utf-8")
store = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/ToolboxTuningTest.kt").read_text(encoding="utf-8")
assert "versionCode = 40" in gradle and 'versionName = "0.1.0-alpha.40"' in gradle
assert "applicationId = \"dev.lolokeksu.legioncontrol\"" in gradle
assert "HapticLabSchema.VERSION" in store, "Do not change SQLite v12 for an overlay"
assert "GAME TOOLBOX" in main and "GameToolboxActivity::class.java" in main
assert "ToolboxTuningTest" in tests and tests.count("@Test") >= 4

expected = {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS", "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY", "android.permission.WRITE_SETTINGS",
    "android.permission.VIBRATE", "android.permission.SYSTEM_ALERT_WINDOW",
}
actual = {x.get(ns + "name") for x in manifest.findall("uses-permission")}
assert actual == expected, (actual, expected)
for forbidden in (
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
):
    assert forbidden not in actual
assert len(manifest.findall("./application/receiver")) == 0
service_entries = [x for x in manifest.findall("./application/service")
    if x.get(ns + "name") == ".GameToolboxService"]
assert len(service_entries) == 1
declared_service = service_entries[0]
assert declared_service.get(ns + "exported") == "false"
assert declared_service.get(ns + "foregroundServiceType") == "specialUse"
properties = declared_service.findall("property")
assert any(p.get(ns + "name") == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    for p in properties)
activities = [x for x in manifest.findall("./application/activity")
    if x.get(ns + "name") == ".GameToolboxActivity"]
assert len(activities) == 1 and activities[0].get(ns + "exported") == "false"
assert "Settings.canDrawOverlays(this)" in activity
assert "ACTION_MANAGE_OVERLAY_PERMISSION" in activity
assert "ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS" in activity
assert "SmartSystemControls.writeSettingsIntent(this)" in activity
assert "getLaunchIntentForPackage" in activity and "CATEGORY_LAUNCHER" in activity
assert "PackageManager.ResolveInfoFlags" in activity
assert "GameToolboxService.start(this)" in activity and "GameToolboxService.stop(this)" in activity
assert "TYPE_APPLICATION_OVERLAY" in service and "FLAG_NOT_FOCUSABLE" in service
assert "FLAG_NOT_TOUCH_MODAL" in service
assert "Settings.canDrawOverlays(this)" in service
assert "START_NOT_STICKY" in service and "startForeground(" in service
assert "stopSelf()" in service and "removeView(" in service
assert "handler.removeCallbacks(clock)" in service and "onDestroy()" in service
assert "ACTION_STOP" in service and ".addAction(" in service
assert "setOnTouchListener(DragHandler())" in service
assert "ToolboxTuning.position(" in service and "ToolboxTuning.sizeDp(" in service
assert "ToolboxTuning.opacity(" in service and "ToolboxTuning.volumeStep(" in service
assert "AudioManager.STREAM_MUSIC" in service
assert "SmartSystemControls.setDoNotDisturb(this, true)" in service
assert "SmartSystemControls.setDoNotDisturb(this, false)" in service
assert "SmartSystemControls.setBrightness(this, 50)" in service
assert "SmartSystemControls.restoreScreenSettings(this)" in service
assert "return START_NOT_STICKY" in service
assert "Settings.System.canWrite(context)" in system
assert all(block not in service for block in (
    "MediaProjection", "AccessibilityService", "queryUsageStats",
    "queryEvents(", "Socket(", "HttpURLConnection", "takeScreenshot",
))
assert all(block not in activity for block in ("QUERY_ALL_PACKAGES", "READ_EXTERNAL_STORAGE"))
print("PASS alpha24: exact permission allowlist, opt-in overlay, non-focusable UI, manual controls, FGS stop and no SQLite migration")
