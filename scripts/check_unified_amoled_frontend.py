#!/usr/bin/env python3
"""Frontend-only guard: five established tabs and one shared theme for every module."""
from pathlib import Path
import xml.etree.ElementTree as ET

base = Path(__file__).resolve().parents[1]
src = base / "app/src/main/java/dev/lolokeksu/legioncontrol"
read = lambda name: (src / name).read_text(encoding="utf-8")
skin = read("LegionUiSupport.kt")
host = read("NavigationHostActivity.kt")
hub = read("HubPageFragment.kt")
toolbox = read("GameToolboxFragment.kt")
assert "fun styleModuleTree(root: View)" in skin
assert "fun styleFragmentButtons(root: View) = styleModuleTree(root)" in skin
assert "fun setModuleActivityContent(activity: Activity, content: ScrollView, subtitle: String)" in skin
assert all(v in skin for v in ("PANEL", "BORDER", "ACCENT", "MUTED",
    "RippleDrawable", "state_enabled", "state_focused", "styleCheck", "styleInput"))
assert all(v in hub and v in toolbox for v in ("LegionUiSupport.MUTED",
    "LegionUiSupport.ACCENT", "LegionUiSupport.BORDER", "LegionUiSupport.PANEL"))
assert "fragment !is HubPageFragment && fragment !is GameDetailFragment" in host
assert "fragment !is GameToolboxFragment" in host
assert "LegionUiSupport.styleFragmentButtons(view)" in host
assert "openRequestedTab(intent)" in host
assert "override fun onNewIntent(intent: Intent)" in host
assert "setLaunchSingleTop(true)" in host and "setRestoreState(true)" in host
for name in ("CaptureStudioActivity.kt", "MainActivity.kt", "GamepadLiveActivity.kt"):
    assert "LegionUiSupport.setModuleActivityContent(this" in read(name), name
assert "setModuleActivityContent" not in read("DisplayPatternActivity.kt")
graph = ET.parse(base / "app/src/main/res/navigation/legion_nav_graph.xml")
ns = "{http://schemas.android.com/apk/res/android}"
nodes = graph.getroot().findall("fragment")
assert len(nodes) == 20
assert len([n for n in nodes if n.get(ns + "name", "").endswith("HubPageFragment")]) == 5
assert '"Домой", "Игры", "Сервисы", "История", "Настройки"' in skin
assert "android.permission.QUERY_ALL_PACKAGES" not in (
    base / "app/src/main/AndroidManifest.xml").read_text()
print("PASS unified frontend: 5 stable hub tabs, 20 destinations, 3 chrome-wrapped Activities, dedicated display test preserved")
