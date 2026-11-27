#!/usr/bin/env python3
"""Regression guard for the reviewed AMOLED design-system cleanup.

Static checks protect the exact source wiring. Rendering is verified separately
by the Android 35 emulator smoke test on the built APK.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/dev/lolokeksu/legioncontrol"


def read(name: str) -> str:
    return (SRC / (name + ".kt")).read_text(encoding="utf-8")


support = read("LegionUiSupport")
hub = read("HubPageFragment")
toolbox = read("GameToolboxFragment")
detail = read("GameDetailFragment")
studio = read("CaptureStudioActivity")
session = read("GameSessionFragment")

assert "fun actionTile(context: Context, title: String, detail: String," in support
assert 'contentDescription = if (detail.isBlank()) title else "$title. $detail"' in support
assert "fun cardSurface(view: View)" in support
for name, code in (("HubPageFragment", hub), ("GameToolboxFragment", toolbox)):
    assert "LegionUiSupport.actionTile(requireContext(), title," in code, name
assert "LegionUiSupport.reportSurface(info)" in detail
assert "LegionUiSupport.actionTile(requireContext(), title, desc, callback)" in detail
assert "getApplicationIcon(pkg)" in detail

library = studio[studio.index("private fun renderMedia("):studio.index("private fun clipActions(")]
assert "LegionUiSupport.actionTile(this, item.name," in library
assert "item.format.name" in library
assert "clipActions(item) else screenshotActions(item)" in library

assert "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE" in session
assert "GameSessionRecorderService.start(requireContext(),pkg,label,tcpOption.isChecked)" in session
for action in ("stopRecording()", "SessionLibraryActivity.intent(requireContext(), pkg)"):
    assert action in session
print("PASS AMOLED audit: dynamic media buttons, shared tiles, accessibility and recording entry points")
