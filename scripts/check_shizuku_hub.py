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
assert len(activities) == 19 and len(set(activities)) == 19, activities
assert 'R.id.toolsFragment' in hub
ci = (root / ".github/workflows/android.yml").read_text()
assert "inputs.full_emulator == true" in ci and "default: false" in ci
assert "testDebugUnitTest" in ci and "scripts/verify_apk.py" in ci
print("PASS: optional Shizuku, shell-only diagnostics, unified 19-module catalog and opt-in emulator")
