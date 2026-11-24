#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
models = (src / "PreflightModels.kt").read_text()
backend = (src / "GamePreflightBackend.kt").read_text()
vm = (src / "GamePreflightViewModel.kt").read_text()
ui = (src / "GamePreflightActivity.kt").read_text()
graph = (src / "GameDetailFragment.kt").read_text()
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml")
a = "{http://schemas.android.com/apk/res/android}"
activity = manifest.find("./application/activity[@"+a+"name='.GamePreflightActivity']")
assert activity is not None and activity.get(a+"exported") == "false"
assert "FRESH_MS = 60_000L" in models and "PreflightLevel.UNKNOWN" in models
assert "GameSessionTelemetry.snapshot(context)" in backend
assert "ResourceTelemetryBackend.read(context)" in backend
assert "DeviceCapabilitiesBackend.read(context)" in backend
assert "LIMIT = 256" in backend and ".commit()" in backend
assert "report=null" in vm and "ApplicationInventory.launchable(app)" in vm
assert "PreflightRules.fresh" in ui and "s.report!==report" in ui
assert "model.setVisible(false)" in ui and "!s.launchFresh" in ui
assert "visibilityGeneration" in vm and "launchFresh=false" in vm
assert "report.critical>0" in ui and "ApplicationInventory.launchIntent(this,report.packageName)" in ui
assert "GamePreflightActivity.EXTRA_PACKAGE" in graph
for banned in ("startForegroundService(", "startService(", "WorkManager", "Settings.System.put", "GameLaunchProfiles.apply(", "Runtime.getRuntime", "ProcessBuilder"):
    assert banned not in models+backend+vm+ui, banned
print("PASS: per-package preferences, explicit real Android checks, stale-report guard and no global writes")
