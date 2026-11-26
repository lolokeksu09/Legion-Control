#!/usr/bin/env python3
"""Approved home wireframe, unchanged route keys, real entry points and fallback search."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
read=lambda n:(src/n).read_text()
hub=read("HubPageFragment.kt")
tabs=read("HubNavigationRules.kt")
host=read("LegionHubActivity.kt")
skin=read("LegionUiSupport.kt")
assert 'TOOLS("tools", "Система", "Система")' in tabs
assert 'HISTORY("history", "Аналитика", "Аналитика")' in tabs
assert "centerGrid(HubCenter.entries.toList())" in hub
for name in ("GAMES","DEVICE","MEDIA","PERIPHERALS","ANALYTICS"):
    assert f"HubCenter.{name} ->" in hub
assert "centers.chunked(2)" in hub and "fontScale < 1.35f" in hub
assert "getApplicationIcon(app.packageName)" in hub
assert "pagination.visibility = if(result.pageCount > 1)" in hub
assert "FeatureFinder.search(filter)" in hub and "catalogExpanded" in hub
assert "HubCenterIcons.tabs()" in host and "HubCenterIcons.tabs()" in skin
for name in ("home","games","device","media","peripherals","analytics","settings"):
    assert ET.parse(root/f"app/src/main/res/drawable/ic_hub_{name}.xml").getroot().tag=="vector"
assert "Готов к запуску" not in hub
assert "SessionLibraryActivity.intent(requireContext(), session.packageName, session.id)" in hub
print("PASS: approved five-center dashboard, stable navigation keys, real icon fallback and searchable legacy tools")
