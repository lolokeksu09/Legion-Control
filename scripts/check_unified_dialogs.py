#!/usr/bin/env python3
"""All app-owned alert builders must use the shared presentation; OS prompts stay native."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
support = (src / "LegionDialogs.kt").read_text()
theme = (root / "app/src/main/res/values/legion_dialogs.xml").read_text()
assert "AlertDialog.Builder(context, R.style.LegionDialogTheme)" in support
assert "setCustomTitle" in support and "styleButtonSurface" in support
assert "styleModuleTree" in support and "setBackgroundDrawable" in support
assert all(token in theme for token in ("#12161C", "#86B7FF", "#9CA5B0", "48dp"))
assert all(token in support for token in (
    "delegate.setItems", "delegate.setSingleChoiceItems", "delegate.setMultiChoiceItems",
    "delegate.setPositiveButton", "delegate.setNegativeButton", "delegate.setView",
    "setOnShowListener", "SOFT_INPUT_ADJUST_RESIZE"))
callers = {}
for path in sorted(src.rglob("*.kt")):
    body = path.read_text()
    if path.name != "LegionDialogs.kt":
        assert not re.search(r"AlertDialog\s*\.\s*Builder\s*\(", body), path.name
        count = body.count("LegionDialogs.builder(")
        if count:
            callers[path.name] = count
for file in ("AudioLabFragment.kt", "HapticLabFragment.kt", "GamePreflightActivity.kt"):
    assert "LegionUiSupport.spinner(" in (src / file).read_text(), file
assert "LegionDialogs.listAdapter(" in (src / "GamePreflightActivity.kt").read_text()
assert "Theme_Black_NoTitleBar_Fullscreen" in (src / "GamingDiagnosticsFragment.kt").read_text()
assert "setContentView(surface)" in (src / "DisplayPatternActivity.kt").read_text()
print(f"PASS unified dialogs: {sum(callers.values())} app-owned dialogs in {len(callers)} callers")

frame = (src / "LegionChartFrame.kt").read_text()
assert "fun axes(" in frame and "fun empty(" in frame and "fun y(" in frame
for name in ("MonitorChartView.kt", "GameSessionTimelineView.kt", "SessionCompareChart.kt"):
    body = (src / name).read_text()
    assert "LegionChartFrame(this)" in body and "frame.axes(" in body and "frame.empty(" in body, name
for name in ("AudioLabFragment.kt", "HapticLabFragment.kt", "ConnectivityLabFragment.kt",
             "ApplicationCenterFragment.kt", "ExperimentLabFragment.kt", "SmartProfilesFragment.kt",
             "HardwareLabFragment.kt", "CompatibilityLabFragment.kt", "GamingDiagnosticsFragment.kt"):
    assert "LegionUiSupport.reportSurface(" in (src / name).read_text(), name
for name in ("HubPageFragment.kt", "GameDetailFragment.kt", "CaptureStudioActivity.kt"):
    body = (src / name).read_text()
    assert "Notice.ERROR" in body and "Notice.LOADING" in body, name
print("PASS shared graph frames and compact status/report components")
