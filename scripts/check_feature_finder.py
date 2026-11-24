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
    "GAMING": "GamingDiagnosticsFragment.kt",
    "HARDWARE": "HardwareLabFragment.kt",
    "COMPAT": "CompatibilityLabFragment.kt",
    "EXPERIMENT": "ExperimentLabFragment.kt",
    "SIGNALS": "SessionSignalsFragment.kt",
    "INCIDENTS": "SessionIncidentsFragment.kt",
    "BASELINES": "SessionBaselinesFragment.kt",
    "DIAGNOSTICS": "MainActivity.kt",
    "GAMEPAD": "GamepadLiveActivity.kt",
    "MONITOR": "LiveMonitorActivity.kt",
    "PREFLIGHT": "GamePreflightActivity.kt",
    "COMPARE": "SessionCompareActivity.kt",
    "LIBRARY": "SessionLibraryActivity.kt",
}
entries = re.findall(r'FeatureEntry\("([^"]+)", FeatureModule\.(\w+), "([^"]+)", "([^"]+)", "([^"]+)"\)', registry)
assert len(entries) == 79, len(entries)
assert len({e[1] for e in entries}) == 21
assert len({e[0] for e in entries}) == len(entries)
for key, module, title, anchor, _ in entries:
    source = (src / files[module]).read_text()
    assert f'"{anchor}"' in source, (key, anchor)
    if module in {"CAPTURE", "MONITOR", "PREFLIGHT", "COMPARE", "LIBRARY"}:
        helper_name = "action" if module in {"CAPTURE", "MONITOR"} else "button"
        assert f'{helper_name}("{anchor}", "{key}")' in source, key
    assert f'FeatureModule.{module}' in source, (key, module)
    assert f'FeatureModule.{module} ->' in (src / "HubPageFragment.kt").read_text()
assert "requestRectangleOnScreen" in helper
assert "bound.singleOrNull()" in helper
assert ".distinct().singleOrNull()" in helper
assert "!target.isEnabled" in helper
assert "FeatureFinder.resolve(id, module)" in helper
assert "performClick(" not in helper and "callOnClick(" not in helper
assert "startService(" not in helper and "startActivity(" not in helper
print("PASS: 79 stable feature IDs bind to real controls across 21 modules; reveal never executes actions")
