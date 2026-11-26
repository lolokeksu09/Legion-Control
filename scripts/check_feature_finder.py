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
    "SIGNALS": "SessionLibraryActivity.kt",
    "INCIDENTS": "SessionLibraryActivity.kt",
    "BASELINES": "SessionLibraryActivity.kt",
    "DIAGNOSTICS": "MainActivity.kt",
    "GAMEPAD": "GamepadLiveActivity.kt",
    "MONITOR": "LiveMonitorActivity.kt",
    "PREFLIGHT": "GamePreflightActivity.kt",
    "COMPARE": "SessionLibraryActivity.kt",
    "LIBRARY": "SessionLibraryActivity.kt",
}
entries = re.findall(r'FeatureEntry\("([^"]+)", FeatureModule\.(\w+), "([^"]+)", "([^"]+)", "([^"]+)"\)', registry)
assert len(entries) == 80, len(entries)
assert len({e[1] for e in entries}) == 21
assert len({e[0] for e in entries}) == len(entries)
for key, module, title, anchor, _ in entries:
    source = (src / files[module]).read_text()
    assert f'"{anchor}"' in source, (key, anchor)
    # Every entry is now explicitly bound, independent of mutable display labels.
    assert source.count(f'"{key}"') == 1, key
    if module in {"DIAGNOSTICS", "GAMEPAD"}:
        assert f'FeatureReveal.bind(this, "{key}")' in source, key
    elif module == "TOOLBOX":
        assert re.search(r'tile\("' + re.escape(anchor) + r'", "[^"]+", "' + re.escape(key) + r'"\)', source), key
        assert "FeatureReveal.bind(this,featureId)" in source
    else:
        factory = r'(?:action|control)' if module == "CAPTURE" else r'(?:action|button|addButton)'
        assert re.search(factory + r'\((?:root, )?"' + re.escape(anchor) + r'", "' + re.escape(key) + r'"\)', source), key
        assert re.search(r'FeatureReveal.bind\(this,\s*featureId\)', source), module
    if module in {"SIGNALS", "INCIDENTS", "BASELINES", "COMPARE"}:
        assert f'FeatureModule.{module}' in (src / "SessionAnalyticsSnapshot.kt").read_text(), (key, module)
    else:
        assert f'FeatureModule.{module}' in source, (key, module)
    assert f'FeatureModule.{module} ->' in (src / "HubPageFragment.kt").read_text()
    if module in {"GAMING","HARDWARE","NETWORK","COMPAT","AUDIO","HAPTIC"}:
        assert f'FeatureModule.{module} -> openStandaloneFeature(MainActivity::class.java, args)' in (src / "HubPageFragment.kt").read_text()
        assert f'FeatureModule.{module}' in (src / "DiagnosticsPage.kt").read_text()
        assert 'putString(FeatureFinder.EXTRA_FEATURE_ID,feature)' in (src / "MainActivity.kt").read_text()
capture = (src / files["CAPTURE"]).read_text()
assert "control(value, featureId, onClick).also { root.addView(it) }" in capture
assert "root.addView(CompactActionRow(this, recordStart, replayStart))" in capture
assert "requestRectangleOnScreen" in helper
assert "bound.singleOrNull()" in helper
assert "feature.anchor" not in helper
assert "TextView" not in helper
assert "!target.isEnabled" in helper
assert "FeatureFinder.resolve(id, module)" in helper
assert "performClick(" not in helper and "callOnClick(" not in helper
assert "startService(" not in helper and "startActivity(" not in helper
print("PASS: 80 explicitly bound stable feature IDs across 21 modules; reveal never executes actions")
