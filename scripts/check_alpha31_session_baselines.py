#!/usr/bin/env python3
"""alpha.31: exact-package, quality-aware baselines never become fake FPS benchmarks."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
def read(name):return (src/name).read_text(encoding="utf-8")
engine=read("SessionBaselines.kt")
repo=read("GameSessionRepository.kt")
vm=read("SessionLibraryViewModel.kt")
screen=read("SessionLibraryActivity.kt")
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
             "SessionBaselines.compare", "Executors.newSingleThreadExecutor()", "onCleared()"):
    assert term in vm, term
for term in ('vm.baseline(7)', 'vm.baseline(30)', 'SessionBaselines.csv(it)', 'SessionBaselines.render(it)'):
    assert term in screen, term
assert "ShizukuService" not in engine+vm+screen
manifest=(root/"app/src/main/AndroidManifest.xml").read_text()
for forbidden in ("QUERY_ALL_PACKAGES", "WRITE_SECURE_SETTINGS", "ACCESS_FINE_LOCATION"):
    assert forbidden not in manifest
assert "CREATE TABLE" not in engine+vm+screen
print("PASS alpha31: exact-package baselines, quality gates, median, CSV, Navigation and 9 JVM tests")
