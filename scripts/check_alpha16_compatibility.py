"""Alpha.16: inspect real public API modules, UI actions, privacy, old SQLite and version."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
compat = (src/"GamingCompatibilityBackend.kt").read_text(encoding="utf-8")
rules = (src/"ReadinessModels.kt").read_text(encoding="utf-8")
activity = (src/"CompatibilityLabFragment.kt").read_text(encoding="utf-8")
compatActivity=(src/"CompatibilityLabActivity.kt").read_text(encoding="utf-8")
viewmodel = (src/"CompatibilityLabViewModel.kt").read_text(encoding="utf-8")
gamepad = (src/"GamepadLiveActivity.kt").read_text(encoding="utf-8")
hardware = (src/"HardwareLabFragment.kt").read_text(encoding="utf-8")
store = (src/"LocalHistoryStore.kt").read_text(encoding="utf-8")
gradle = (root/"app/build.gradle.kts").read_text(encoding="utf-8")
manifest = ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
a="{http://schemas.android.com/apk/res/android}"
perms={p.attrib.get(a+"name") for p in manifest.findall("uses-permission")}
assert perms=={
 "android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
 "android.permission.ACCESS_WIFI_STATE","android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
},perms
for name in (".CompatibilityLabActivity",".GamepadLiveActivity"):
    matches=[x for x in manifest.findall("./application/activity") if x.attrib.get(a+"name")==name]
    assert len(matches)==1 and matches[0].attrib.get(a+"exported")=="false",name
assert 'CompatibilityLabActivity::class.java' in hardware
assert 'versionCode = 59' in gradle and 'versionName = "0.1.0-alpha.59"' in gradle
assert "HapticLabSchema.VERSION" in store
assert "DROP TABLE" not in store
for n,label in enumerate([
    "Display Modes & HDR", "Audio Output Inspector","Gamepad Inventory",
    "Gamepad Live", "DRM / Widevine Compatibility","Gaming Readiness",
    "Quick Compute Probe"
],1):
    assert f'"{n} · {label}' in activity,(n,label)
assert "Полный отчёт о совместимости" in activity
assert "Intent.ACTION_SEND" in activity
assert "Intent.createChooser" in activity
assert "lastCompletedReport" in activity
assert "viewModel.cancel()" in activity and "viewModel.setVisible(false)" in activity
assert "task?.cancel(true)" in viewmodel and "executor.shutdownNow()" in viewmodel
assert "generation.incrementAndGet()" in viewmodel
assert "ViewModelProvider(this)[CompatibilityLabViewModel::class.java]" in activity
assert "report.observe(viewLifecycleOwner)" in activity
assert "ModuleNavigation.forwardLegacyEntry(this)" in compatActivity
assert "display.supportedModes" in compat
assert "hdrCapabilities.supportedHdrTypes" in compat
assert "display.isWideColorGamut" in compat
assert "manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)" in compat
assert "PROPERTY_OUTPUT_FRAMES_PER_BUFFER" in compat
assert "InputDevice.getDeviceIds()" in compat
assert ".filterNotNull()" in compat, "primitive IntArray must map before filtering"
assert "device.motionRanges.count" in compat
assert "MediaDrm.isCryptoSchemeSupported(widevine)" in compat
assert "MediaDrm(widevine).use" in compat
assert 'getPropertyString("securityLevel")' in compat
assert "drm.openSession" not in compat
assert "networkValidated" in rules
assert "thermalStatus >= 3" in rules
assert "batteryTemperatureDeciC >= 450" in rules
assert "powerSaving" in rules and "memoryLow" in rules
assert "ComputeProbeMath.checksum(" in compat
assert "Thread.currentThread().isInterrupted" in compat
assert "ITERATIONS = 500_000" in rules
assert "dispatchKeyEvent(event: KeyEvent)" in gamepad
assert "onGenericMotionEvent(event: MotionEvent)" in gamepad
assert "event.keyCode != KeyEvent.KEYCODE_BACK" in gamepad
assert "isController(event.deviceId)" in gamepad
assert "ModuleNavigation.open(this,GamepadLiveActivity::class.java)" in activity
assert "onStop()" in activity and "onCleared()" in viewmodel
for forbidden in (
    "WRITE_SECURE_SETTINGS", "BIND_ACCESSIBILITY_SERVICE",
    "registerReceiver(", "startForegroundService(", "Runtime.getRuntime().exec",
    "setPreferredDisplayModeId(", "setCommunicationDevice(", "openSession("
):
    assert forbidden not in activity+viewmodel+gamepad,forbidden
# Android's sticky battery read inside the backend is allowed; no listener exists.
assert "context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))" in compat
print("PASS: seven real compatibility modules, permission boundary, lifecycle and old v5 schema")
