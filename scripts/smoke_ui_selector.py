"""Pure UI hierarchy selection for emulator smoke, not Android application code."""
import re
import xml.etree.ElementTree as ET

TABS = frozenset(("Главная", "Игры", "Инструменты", "История", "Настройки"))


def center(node):
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if m is None:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return ((x1+x2)//2, (y1+y2)//2) if x2>x1 and y2>y1 else None


def locate(tree: ET.Element, needle: str, exact_only: bool = False):
    # Tabs have a stable full accessibility label; visible text may be abbreviated.
    # Prefer the actual actionable tab over any explanatory text elsewhere.
    if needle in TABS:
        tabs = [n for n in tree.iter("node")
                if n.get("content-desc") == "Открыть раздел " + needle
                and n.get("clickable") == "true" and n.get("enabled", "true") == "true"
                and center(n) is not None]
        if len(tabs) == 1:
            return center(tabs[0])
        if len(tabs) > 1:
            raise AssertionError("Ambiguous bottom navigation target: " + needle)
    candidates = []
    for node in tree.iter("node"):
        label = node.get("text", "")
        desc = node.get("content-desc", "")
        exact = label == needle or desc == needle
        partial = needle.casefold() in (label + " " + desc).casefold()
        if not partial or (exact_only and not exact):
            continue
        point = center(node)
        if point is not None:
            candidates.append((0 if exact else 1, *point))
    if not candidates:
        return None
    candidates.sort()
    return candidates[0][1], candidates[0][2]
