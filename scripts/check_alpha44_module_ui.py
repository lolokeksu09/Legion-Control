#!/usr/bin/env python3
"""Alpha.44: legacy entry points cannot bypass AMOLED styling or Game Hub."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
read = lambda name: (src / name).read_text(encoding="utf-8")
legacy = (
    "GamingDiagnosticsActivity", "GameSessionActivity", "SmartProfilesActivity",
    "AudioLabActivity", "HapticLabActivity", "ApplicationCenterActivity",
    "HardwareLabActivity", "CompatibilityLabActivity",
    "ConnectivityLabActivity", "ExperimentLabActivity"
)
for name in legacy:
    body = read(name + ".kt")
    assert "ModuleNavigation.forwardLegacyEntry(this)" in body, name
    assert ".replace(container.id" not in body, name
    assert "setContentView(container)" not in body, name

module = read("ModuleNavigation.kt")
assert "HubDestinationRoutes.destinationFor(activity.javaClass)" in module
assert "Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP" in module
assert "putExtra(EXTRA_MODULE_ROUTE, route)" in module
assert "putExtra(EXTRA_MODULE_ARGS, Bundle(it))" in module
assert "activity.finish()" in module

host = read("NavigationHostActivity.kt")
assert "private fun openRequestedModule(request: Intent)" in host
assert "request.removeExtra(ModuleNavigation.EXTRA_MODULE_ROUTE)" in host
assert "request.removeExtra(ModuleNavigation.EXTRA_MODULE_ARGS)" in host
assert "if (!permitted) return" in host
assert "nav.navigate(route, args" in host
assert "openRequestedModule(intent)" in host
for name in (
    "gameSessionFragment", "smartProfilesDestination", "applicationCenterDestination",
    "audioLabDestination", "hapticLabDestination", "gamingDiagnosticsDestination",
    "hardwareLabDestination", "compatibilityLabDestination",
    "connectivityLabDestination", "experimentLabDestination"
):
    assert "R.id." + name in host, name

fragments = (
    "GamingDiagnosticsFragment", "GameSessionFragment", "SessionSignalsFragment",
    "SessionIncidentsFragment", "SessionBaselinesFragment",
    "ApplicationCenterFragment", "SmartProfilesFragment", "AudioLabFragment",
    "HapticLabFragment", "ConnectivityLabFragment", "ExperimentLabFragment",
    "CompatibilityLabFragment", "HardwareLabFragment"
)
for name in fragments:
    assert "LegionUiSupport.styleModuleTree(view)" in read(name + ".kt"), name

skin = read("LegionUiSupport.kt")
for color in ("0xFF12161C", "0xFF252B33", "0xFF86B7FF", "0xFF9CA5B0"):
    assert color in skin, color
assert 'sectionLabel(requireContext(), "ПРОВЕРКИ УСТРОЙСТВА")' in read("GamingDiagnosticsFragment.kt")
assert 'sectionLabel(requireContext(), "УПРАВЛЕНИЕ ЗАПИСЬЮ")' in read("GameSessionFragment.kt")
assert "LegionUiSupport.ACCENT" in read("GameSessionTimelineView.kt")

gradle = (root / "app/build.gradle.kts").read_text()
assert "versionCode = 49" in gradle
assert 'versionName = "0.1.0-alpha.49"' in gradle
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
assert "android.permission.QUERY_ALL_PACKAGES" not in manifest
assert "android.permission.WRITE_SECURE_SETTINGS" not in manifest
assert "android.permission.MANAGE_EXTERNAL_STORAGE" not in manifest
print("PASS alpha44: ten legacy entrypoints use Game Hub; 13 modules independently themed")
