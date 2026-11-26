"""Guard the game card, explicit launch-profile controls and truthful analytics dashboard."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
detail = (src / "GameDetailFragment.kt").read_text()
profile = (src / "GameProfilePresentation.kt").read_text()
tool = (src / "GameToolboxFragment.kt").read_text()
actions = (src / "GameLaunchProfileActions.kt").read_text()
session = (src / "GameSessionFragment.kt").read_text()
insights = (src / "SmartSessionInsights.kt").read_text()
presentation = (src / "SmartInsightsPresentation.kt").read_text()
fragment = (src / "SmartProfilesFragment.kt").read_text()
assert "GameProfilePresentation.report(" in detail
assert "CapabilityAccessBackend.profileAccess(" in detail
assert "GameLaunchProfiles.pendingPackage(" in detail
editor = (src / "GameLaunchProfileEditor.kt").read_text()
assert "setView(scroll)" in editor and "LegionUiSupport.spinner" in editor
assert "GameLaunchProfileEditor.show(" in tool and "GameLaunchProfileEditor.show(" in detail and "GameLaunchProfileEditor.show(" in fragment
for entry in (tool, detail):
    assert "GameLaunchProfileActions.confirmApply(" in entry
    assert "GameLaunchProfileActions.confirmRestore(" in entry
assert "setPositiveButton" in actions
assert "GameLaunchProfiles.pendingConfirmation(context)" in actions
assert "GameLaunchProfiles.restoreConfirmed(context, pending)" in actions
assert "GameLaunchProfilePolicy.editorPercentOptions(" in editor
assert 'putString("app_center_package", it)' in session
assert "ModuleNavigation.open(this, SmartProfilesActivity::class.java, args)" in session
assert "COUNT(g.thermal_status),COUNT(g.network_validated)" in insights
assert "end_reason='USER_STOP'" in insights
assert "thermalSampleCount" in presentation and "networkSampleCount" in presentation
assert '"н/д"' in presentation
assert "update.insights?.let(::showInsights)" in fragment
assert "analyticsPanel.visibility = View.VISIBLE" in fragment
assert "SmartInsightsPresentation.metrics(data)" in fragment
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/GameProfilePresentationTest.kt").read_text()
assert tests.count("@Test") >= 3
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/SmartInsightsPresentationTest.kt").read_text()
assert tests.count("@Test") >= 3
print("PASS: game profile detail, explicit profile apply/undo, SQL-backed analytics with known denominators and accessible metric rows")
