#!/usr/bin/env python3
"""alpha.29: one central Navigation Component route for every module with retained Activity back-compat."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
nav=ET.parse(root/"app/src/main/res/navigation/legion_nav_graph.xml").getroot()
manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
android="{http://schemas.android.com/apk/res/android}"
ids=set()
nodes=nav.findall("activity")
expected={
    "MainActivity": "diagnosticsDestination",
    "GameToolboxActivity": "gameToolboxDestination",
    "CaptureStudioActivity": "captureStudioDestination",
    "GamingDiagnosticsActivity": "gamingDiagnosticsDestination",
    "GamepadLiveActivity": "gamepadLiveDestination",
    "DisplayPatternActivity": "displayPatternDestination"
}
assert len(nodes)==len(expected), (len(nodes),len(expected))
registered={a.get(android+"name") for a in manifest.findall("./application/activity")}
mapping=(src/"HubDestinationRoutes.kt").read_text()
for node in nodes:
    cls=node.get(android+"name").split(".")[-1]
    route=node.get(android+"id")
    assert cls in expected and route=="@+id/"+expected[cls],(cls,route)
    assert "."+cls in registered
    assert f"{cls}::class.java to R.id.{expected[cls]}" in mapping
    assert cls not in ids
    ids.add(cls)
assert ids==set(expected)
assert not any(n.get(android+"id")=="@+id/gameSessionFragment" for n in nodes)
frags={n.get(android+"id"):n for n in nav.findall("fragment")}
assert len(frags)==15 and "@+id/gameSessionFragment" in frags
assert frags["@+id/applicationCenterDestination"].get(android+"name").endswith(".ApplicationCenterFragment")
assert "ApplicationCenterActivity::class.java to R.id.applicationCenterDestination" in mapping
assert ".ApplicationCenterActivity" in registered
assert frags["@+id/smartProfilesDestination"].get(android+"name").endswith(".SmartProfilesFragment")
assert "SmartProfilesActivity::class.java to R.id.smartProfilesDestination" in mapping
assert ".SmartProfilesActivity" in registered
assert "SmartProfilesFragment()" in (src/"SmartProfilesActivity.kt").read_text()
assert frags["@+id/audioLabDestination"].get(android+"name").endswith(".AudioLabFragment")
assert "AudioLabActivity::class.java to R.id.audioLabDestination" in mapping
assert ".AudioLabActivity" in registered
assert "AudioLabFragment()" in (src/"AudioLabActivity.kt").read_text()
assert frags["@+id/hapticLabDestination"].get(android+"name").endswith(".HapticLabFragment")
assert "HapticLabActivity::class.java to R.id.hapticLabDestination" in mapping
assert ".HapticLabActivity" in registered
assert "HapticLabFragment()" in (src/"HapticLabActivity.kt").read_text()
assert frags["@+id/connectivityLabDestination"].get(android+"name").endswith(".ConnectivityLabFragment")
assert "ConnectivityLabActivity::class.java to R.id.connectivityLabDestination" in mapping
assert ".ConnectivityLabActivity" in registered
assert "ConnectivityLabFragment()" in (src/"ConnectivityLabActivity.kt").read_text()
assert frags["@+id/experimentLabDestination"].get(android+"name").endswith(".ExperimentLabFragment")
assert "ExperimentLabActivity::class.java to R.id.experimentLabDestination" in mapping
assert ".ExperimentLabActivity" in registered
assert "ExperimentLabFragment()" in (src/"ExperimentLabActivity.kt").read_text()
assert frags["@+id/compatibilityLabDestination"].get(android+"name").endswith(".CompatibilityLabFragment")
assert "CompatibilityLabActivity::class.java to R.id.compatibilityLabDestination" in mapping
assert ".CompatibilityLabActivity" in registered
assert "CompatibilityLabFragment()" in (src/"CompatibilityLabActivity.kt").read_text()
assert frags["@+id/hardwareLabDestination"].get(android+"name").endswith(".HardwareLabFragment")
assert "HardwareLabActivity::class.java to R.id.hardwareLabDestination" in mapping
assert ".HardwareLabActivity" in registered
assert "HardwareLabFragment()" in (src/"HardwareLabActivity.kt").read_text()
assert "ApplicationCenterFragment()" in (src/"ApplicationCenterActivity.kt").read_text()
assert frags["@+id/gameSessionFragment"].get(android+"name").endswith(".GameSessionFragment")
game=(src/"GameSessionFragment.kt").read_text()
compat=(src/"GameSessionActivity.kt").read_text()
assert "GameSessionRecorderService.start(requireContext()," in game
assert "GameSessionFragment()" in compat
hub=(src/"HubPageFragment.kt").read_text()
detail=(src/"GameDetailFragment.kt").read_text()
assert "HubDestinationRoutes.destinationFor(cls)" in hub+detail
assert "NavHostFragment.findNavController(this).navigate(route, extras)" in hub
assert "NavHostFragment.findNavController(this).navigate(route, intent.extras)" in detail
assert "NavHostFragment.findNavController(this).navigate(" in detail
assert 'putString("smart_package",pkg)' in detail
assert "if (route != null)" in hub and "else startActivity(intent)" in detail
assert "startActivity(Intent(requireContext(), cls)" in hub
assert "GameSessionActivity::class.java" in hub
assert ".GameSessionActivity" in registered
assert nav.get("{http://schemas.android.com/apk/res-auto}startDestination")=="@id/homeFragment"
assert "QUERY_ALL_PACKAGES" not in (root/"app/src/main/AndroidManifest.xml").read_text()
print("PASS alpha29: 6 ActivityNavigator routes and nine real module Fragments with explicit-intent compatibility")
