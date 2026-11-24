"""Strict state checks for Android 35 workload measurements."""
import re

def visible_overlay(dump, package):
    # dumpsys window windows includes individual WindowState records even
    # when uiautomator dumps only the focused Activity root.
    blocks = re.split(r"(?m)^\s*Window #\d+\s+Window\{", dump)
    for block in blocks[1:]:
        header = block.splitlines()[0]
        if not re.search(r"(?<![\w.])" + re.escape(package) + r"(?![\w.])", header):
            continue
        if not re.search(r"\bty=(?:APPLICATION_OVERLAY|2038)\b", block):
            continue
        if "mHasSurface=true" not in block:
            continue
        if re.search(r"\bmViewVisibility=0x0\b", block) and not re.search(r"\bisVisible=false\b", block):
            return True
    return False
