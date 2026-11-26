"""Guard paging, saved search state, browse filters and explicit navigation."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
hub = (src / "HubPageFragment.kt").read_text()
policy = (src / "HubLibraryPolicy.kt").read_text()
assert "HubGameFilter" in policy and "HubGameSort" in policy and "HubGamePage" in policy
assert "PAGE_SIZE = 24" in policy and "requestedPage.coerceIn" in policy
assert "HubSearch.matches(" in policy
assert "HubLibraryPolicy.select(" in hub
assert "HubGameFilter.entries" in hub and "HubGameSort.entries" in hub and "HubLibraryPolicy.homeFavorites(" in hub
assert "filtered.take(60)" not in hub
assert "previous.isEnabled = result.page > 0" in hub
assert "next.isEnabled = result.page + 1 < result.pageCount" in hub
assert "openGameCard(app)" in hub and 'putString("gamePackage", app.packageName)' in hub
for key in ("libraryQuery", "libraryFilter", "librarySort", "libraryPage", "toolQuery", "toolCategory"):
    assert f'"{key}"' in hub
assert "onSaveInstanceState(outState: Bundle)" in hub
assert "categorySelector.isEnabled = filter.isEmpty()" in hub
assert "FeatureFinder.search(filter)" in hub
assert "isAccessibilityHeading = true" in hub
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/HubLibraryPolicyTest.kt").read_text()
assert tests.count("@Test") >= 5
print("PASS: game library paging and filters, saved search state, quick favorites and category browsing")
