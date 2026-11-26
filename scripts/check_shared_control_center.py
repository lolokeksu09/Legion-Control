"""Guard canonical manual controls, launch profile editor and compact analytics."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
read = lambda name: (src / (name + ".kt")).read_text()
panel = read("SystemControlPanel")
editor = read("GameLaunchProfileEditor")
smart = read("SmartProfilesFragment")
toolbox = read("GameToolboxFragment")
detail = read("GameDetailFragment")
hub = read("HubPageFragment")
for screen in (smart, toolbox, hub):
    assert "SystemControlPanel.show(requireContext())" in screen
for screen in (smart, toolbox, detail):
    assert "GameLaunchProfileEditor.show(" in screen
    assert "GameLaunchProfiles.save(" not in screen
for action in ("setMediaVolume", "setBrightness", "setDoNotDisturb", "setScreenTimeout", "restoreScreenSettings"):
    assert "SmartSystemControls." + action in panel
    assert "SmartSystemControls." + action not in smart
assert "SmartCompatibilityBackend.inspect(ctx)" in panel
assert "value !in min..100" in panel
assert "seconds.getOrNull(timeout.selectedItemPosition - 1)" in panel
assert "GameLaunchProfiles.save(ctx, pkg, profile)" in editor
assert "GameLaunchProfiles.apply" not in editor
assert "GameLaunchProfilePolicy.editorPercentOptions(" in editor
assert "Системное управление" in hub
assert "SmartInsightsPresentation.report(data)" in smart
assert 'putExtra(Intent.EXTRA_TEXT, SmartInsightsPresentation.report(data))' in smart
assert "result.text = SmartInsightsPresentation.report(data)" not in smart
assert smart.index("analyticsPanel = LinearLayout") < smart.index("СЕТЕВАЯ ДИАГНОСТИКА")
assert "LegionUiSupport.spinner(requireContext(), listOf" in smart
assert '"profile.brightness"' in smart and '"Параметры Android"' in smart
print("PASS: one manual controls panel, one launch editor, explicit actions and nonduplicated analytics")
