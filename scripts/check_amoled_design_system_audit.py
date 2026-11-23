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
assert "LegionUiSupport.cardSurface(this)" in detail
assert 'contentDescription = if (desc.isBlank()) title else "$title. $desc"' in detail

for kind, start, end, accessibility in (
    ("MP4", "for (clip in clips)", "private fun clipActions(", 'contentDescription = "Клип:'),
    ("PNG", "for (shot in shots)", "private fun screenshotActions(", 'contentDescription = "Снимок:'),
):
    block = studio[studio.index(start):studio.index(end)]
    assert "LegionUiSupport.styleAction(this)" in block, kind
    assert accessibility in block, kind
    assert "setOnClickListener {" in block, kind

prepare = session.index('sectionLabel(requireContext(), "ПОДГОТОВКА")')
start = session.index('action("НАЧАТЬ ЗАПИСЬ")')
extras = session.index('sectionLabel(requireContext(), "ДОПОЛНИТЕЛЬНЫЕ НАСТРОЙКИ")')
analytics = session.index('sectionLabel(requireContext(), "АНАЛИТИКА И ИСТОРИЯ")')
assert prepare < start < extras < analytics
assert "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE" in session
assert "GameSessionRecorderService.start(requireContext(),pkg,label,tcpOption.isChecked)" in session
assert 'action("ОСТАНОВИТЬ ЗАПИСЬ")' in session
assert 'action("Smart Profiles · сохранённые настройки")' in session
assert 'action("Session Signals · хронология и события")' in session
print("PASS AMOLED audit: dynamic media buttons, shared tiles, a11y descriptions, recording hierarchy")
