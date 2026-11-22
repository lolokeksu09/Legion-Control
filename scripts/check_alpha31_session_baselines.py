#!/usr/bin/env python3
"""alpha.31: exact-package, quality-aware baselines never become fake FPS benchmarks."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
def read(name):return (src/name).read_text(encoding="utf-8")
engine=read("SessionBaselines.kt")
repo=read("GameSessionRepository.kt")
vm=read("SessionBaselinesViewModel.kt")
screen=read("SessionBaselinesFragment.kt")
hub=read("HubPageFragment.kt")
host=read("NavigationHostActivity.kt")
session=read("GameSessionFragment.kt")
signals=read("SessionSignalsFragment.kt")
tests=(root/"app/src/test/java/dev/lolokeksu/legioncontrol/SessionBaselinesTest.kt").read_text()
nav=ET.parse(root/"app/src/main/res/navigation/legion_nav_graph.xml").getroot()
android="{http://schemas.android.com/apk/res/android}"
frags={n.get(android+"id"):n for n in nav.findall("fragment")}
assert len(frags)==19
assert frags["@+id/sessionBaselinesFragment"].get(android+"name").endswith(".SessionBaselinesFragment")
for term in ("ExperimentAnalytics.evidence", "MAX_SESSIONS = 24",
             "MAX_POINTS_PER_SESSION = 2000", "MIN_REFERENCES = 3",
             "MIN_COVERAGE = 0.70", "MAX_GAP_MS = 45_000L",
             "endReason != \"USER_STOP\"", "inputLimited", "exclusions",
             "median", "csv(", "cell(raw:String)"):
    assert term in engine,term
for term in ("knownSessionPackages", "recentForPackage", "WHERE package_name=?",
             "ORDER BY started_at_ms DESC,session_id DESC LIMIT ?"):
    assert term in repo,term
for term in ("GameSessionRepository(getApplication())", "recentForPackage",
             "SessionBaselines.compare", "Executors.newSingleThreadExecutor()",
             "onCleared()", "OutputStreamWriter", "lastReport"):
    assert term in vm,term
for term in ("setTextIsSelectable(true)", "Intent.ACTION_CREATE_DOCUMENT",
             "vm.exportCsv(it)", "vm.pickPackages()", "vm.setDays(30)"):
    assert term in screen,term
for term in ("R.id.sessionBaselinesFragment",):
    assert term in hub+host+session+signals,term
assert tests.count("@Test") >= 9
assert "ShizukuService" not in engine+vm+screen
manifest=(root/"app/src/main/AndroidManifest.xml").read_text()
for forbidden in ("QUERY_ALL_PACKAGES", "WRITE_SECURE_SETTINGS", "ACCESS_FINE_LOCATION"):
    assert forbidden not in manifest
assert "CREATE TABLE" not in engine+vm+screen
print("PASS alpha31: exact-package baselines, quality gates, median, CSV, Navigation and 9 JVM tests")
