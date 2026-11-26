"""Guard the opt-in shell bridge, canonical module registry and fast build policy."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
gradle = (root / "app/build.gradle.kts").read_text()
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
provider = next(p for p in manifest.findall("./application/provider")
                if p.get(ns+"name") == "rikka.shizuku.ShizukuProvider")
assert provider.get(ns+"permission") == "android.permission.INTERACT_ACROSS_USERS_FULL"
assert provider.get(ns+"authorities") == "${applicationId}.shizuku"
assert provider.get(ns+"exported") == "true"
assert 'dev.rikka.shizuku:api:13.1.5' in gradle
assert 'dev.rikka.shizuku:provider:13.1.5' in gradle
bridge = (src / "ShizukuAccessBackend.kt").read_text()
hub = (src / "HubPageFragment.kt").read_text()
catalog = (src / "HubToolCatalog.kt").read_text()
assert "Shizuku.pingBinder()" in bridge and "Shizuku.checkSelfPermission()" in bridge
assert "uid == 2000" in bridge and "Shizuku.checkRemotePermission(permission)" in bridge
assert "Shizuku.requestPermission(REQUEST_CODE)" in bridge
for forbidden in ("Runtime.getRuntime()", "ProcessBuilder(", "newProcess(", "bindUserService(", "HiddenApiBypass"):
    assert forbidden not in bridge
for pair in ("BinderReceived", "BinderDead", "RequestPermissionResult"):
    assert "Shizuku.add" + pair + "Listener(" in hub
    assert "Shizuku.remove" + pair + "Listener(" in hub
assert "ShizukuAccessBackend.inspectShell(ctx)" in hub and "legion-shizuku-inspect" in hub
assert "HubToolCatalog.groups" in hub and "HubToolCatalog.matches(" in hub
activities = re.findall(r'item\("[^"]+", "[^"]+", (\w+)::class.java\)', catalog)
expected = {
    "GameToolboxActivity",
    "SmartProfilesActivity",
    "ApplicationCenterActivity",
    "GameSessionActivity",
    "GamePreflightActivity",
    "CaptureStudioActivity",
    "AudioLabActivity",
    "DisplayPatternActivity",
    "HapticLabActivity",
    "GamepadLiveActivity",
    "GamingDiagnosticsActivity",
    "HardwareLabActivity",
    "CompatibilityLabActivity",
    "MainActivity",
    "ConnectivityLabActivity",
    "ExperimentLabActivity",
    "SessionLibraryActivity",
}
assert len(activities) == len(set(activities)) and set(activities) == expected, activities
assert "LiveMonitorActivity" not in activities
assert 'item("Диагностический центр"' in catalog
assert 'R.id.toolsFragment' in hub
ci = (root / ".github/workflows/android.yml").read_text()
assert "inputs.full_emulator == true" in ci and "default: false" in ci
assert "testDebugUnitTest" in ci and "scripts/verify_apk.py" in ci
print("PASS: optional Shizuku, shell-only diagnostics, unified 17-module catalog and opt-in emulator")

# Scoped read-only Shizuku UserService, no general command API.
audit = (src / "ShizukuSystemAudit.kt").read_text()
service = (src / "ShellAuditService.kt").read_text()
aidl = (root / "app/src/main/aidl/dev/lolokeksu/legioncontrol/IShellAudit.aidl").read_text()
assert "String inspect() = 2;" in aidl and "void destroy() = 16777114;" in aidl
assert "Process.myUid() != 2000" in service
assert "ProcessBuilder(probe.args)" in service and "waitFor(1500L" in service
assert "readNBytes(257)" in service and "destroyForcibly()" in service
assert "daemon(false)" in audit and "unbindUserService(request.args, request, true)" in audit
assert "systemAudit?.cancel()" in hub
assert "cached_apps_freezer" in service and "activity_manager_native_boot" in service
assert "ToolboxGameSelection.select" in (src / "HubRepository.kt").read_text()
assert "ToolboxGameSelection.select" in (src / "GameToolboxFragment.kt").read_text()
assert "ProfileMaintenanceGate.tryRun" in (src / "ToolboxGameSelection.kt").read_text()
