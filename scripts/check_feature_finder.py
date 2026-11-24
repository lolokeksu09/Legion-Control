#!/usr/bin/env python3
"""Check the registry against actual existing action labels; no backend operations."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
registry = (src / "FeatureFinder.kt").read_text()
helper = (src / "FeatureReveal.kt").read_text()
files = {
    "CAPTURE": "CaptureStudioActivity.kt", "AUDIO": "AudioLabFragment.kt",
    "HAPTIC": "HapticLabFragment.kt", "NETWORK": "ConnectivityLabFragment.kt",
    "PROFILE": "SmartProfilesFragment.kt", "APPS": "ApplicationCenterFragment.kt",
    "SESSION": "GameSessionFragment.kt", "TOOLBOX": "GameToolboxFragment.kt",
}
entries = re.findall(r'FeatureEntry\("([^"]+)", FeatureModule\.(\w+), "([^"]+)", "([^"]+)", "([^"]+)"\)', registry)
assert len(entries) == 26, len(entries)
assert len({e[0] for e in entries}) == len(entries)
for key, module, title, anchor, _ in entries:
    source = (src / files[module]).read_text()
    assert f'"{anchor}"' in source, (key, anchor)
    assert f'FeatureModule.{module}' in source, (key, module)
    assert f'FeatureModule.{module} ->' in (src / "HubPageFragment.kt").read_text()
assert "requestRectangleOnScreen" in helper
assert "FeatureFinder.resolve(id, module)" in helper
assert "performClick(" not in helper and "callOnClick(" not in helper
assert "startService(" not in helper and "startActivity(" not in helper
print("PASS: 26 stable feature IDs bind to real controls across eight modules; reveal never executes actions")
