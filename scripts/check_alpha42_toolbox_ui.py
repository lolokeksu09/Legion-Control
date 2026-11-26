#!/usr/bin/env python3
"""Alpha.42 UI contract: real NavHost screen, stable controls, no new Android privileges."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
ui = (src / "GameToolboxFragment.kt").read_text(encoding="utf-8")
legacy = (src / "GameToolboxActivity.kt").read_text(encoding="utf-8")
host = (src / "NavigationHostActivity.kt").read_text(encoding="utf-8")
routes = (src / "HubDestinationRoutes.kt").read_text(encoding="utf-8")
graph = ET.parse(root / "app/src/main/res/navigation/legion_nav_graph.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
destinations = [n for n in graph.findall("fragment")
                if n.get(ns + "id") == "@+id/gameToolboxDestination"]
assert len(destinations) == 1
assert destinations[0].get(ns + "name") == "dev.lolokeksu.legioncontrol.GameToolboxFragment"
assert not any(n.get(ns + "id") == "@+id/gameToolboxDestination"
               for n in graph.findall("activity"))
assert "GameToolboxActivity::class.java to R.id.gameToolboxDestination" in routes
assert 'putExtra("open_game_toolbox", true)' in legacy
assert 'intent.getBooleanExtra("open_game_toolbox", false)' in host
assert "nav.navigate(R.id.gameToolboxDestination)" in host
assert 'R.id.gameToolboxDestination -> "ИГРОВАЯ ПАНЕЛЬ"' in host
assert "actual == R.id.gameToolboxDestination" in host
assert "R.id.applicationCenterDestination, R.id.gameToolboxDestination" in host
for token in (
    "LegionUiSupport.MUTED", "LegionUiSupport.ACCENT",
    "LegionUiSupport.BORDER", "LegionUiSupport.PANEL",
    "section(\"GAME TOOLBOX\"", 'group("Текущее состояние")',
    'group("Запуск панели")', 'group("Игровой профиль")',
    'group("Внешний вид")', "stateDescription = if (checked)",
    "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE",
    "Settings.canDrawOverlays(requireContext())",
    "GameToolboxService.start(requireContext())",
    "GameToolboxService.stop(requireContext())",
    "GameLaunchProfiles.apply(requireContext(), pkg)",
    "GameLaunchProfiles.restore(requireContext())",
    "GameLaunchProfiles.save(ctx, pkg, profile)",\n    "confirmApplyGameLaunchProfile",\n    "confirmRestoreGameLaunchProfile",
    "SmartSystemControls.restoreScreenSettings(requireContext())",
):
    assert token in ui, token
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
assert "android.permission.QUERY_ALL_PACKAGES" not in manifest
assert "android.permission.WRITE_SECURE_SETTINGS" not in manifest
assert "android.permission.BIND_ACCESSIBILITY_SERVICE" not in manifest
assert "class GameToolboxFragment : Fragment()" in ui
assert "versionCode = 49" in (root / "app/build.gradle.kts").read_text()
print("PASS alpha42: shared Game Hub shell, selectable cards, opt-in toolbox and legacy compatibility")
