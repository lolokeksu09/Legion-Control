#!/usr/bin/env python3
"""Preserve bounded analytics, selected identity, CSV snapshot and canonical routes."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
read=lambda n:(src/n).read_text()
ui=read("SessionLibraryActivity.kt")
vm=read("SessionLibraryViewModel.kt")
model=read("SessionAnalyticsSnapshot.kt")
hub=read("HubPageFragment.kt")
catalog=read("HubToolCatalog.kt")
assert "UnifiedGameSessionSnapshot.from(detail)" in vm
assert "detail.header.id==row.id && detail.header.packageName==row.packageName" in vm
assert "selectionGeneration.get()==token" in vm and "selectionGeneration.incrementAndGet()" in vm
assert "SessionSignals.analyze(detail.header, detail.points, detail.events" in model
assert "SessionIncidents.analyze(detail.header, detail.points, detail.events" in model
assert "detail.sampleLimited, detail.eventLimited" in model
assert "snapshot.compareWindow(minutes)" in vm and "snapshot.baselineCapture()" in vm
assert "header.packageName==snapshot.detail.header.packageName" in vm
assert "pendingCsv.begin(snapshot)" in ui and "pendingCsv.take()" in ui
assert "it.write(snapshot.content)" in vm
assert "AnalyticsPage.entries.associateWith" in ui
assert "SessionCompareChart(this)" in ui
for module in ("SIGNALS","INCIDENTS","BASELINES","COMPARE","LIBRARY"):
    assert f"FeatureModule.{module} -> openStandaloneFeature(SessionLibraryActivity::class.java, args)" in hub
assert "SessionCompareActivity::class.java" not in catalog
assert "SessionLibraryActivity.intent(requireContext(), session.packageName, session.id)" in hub
assert "SessionLibraryActivity.intent(requireContext(), pkg)" in read("GameSessionFragment.kt")
assert "analyticsDetail(row.id)" in vm
assert "detail.header.endedAtMs==null" in vm
assert "Only ended records can be archived" in read("SessionLibraryExport.kt")
assert "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE" in ui
assert "returnToReport ?: AnalyticsPage.OVERVIEW" in ui
assert "experiment_package" in ui
assert "FeatureReveal.fromActivity(this,root,featureModule)" in ui
print("PASS: shared session analytics, bounded comparisons, exact snapshot CSV and stable search routes")
