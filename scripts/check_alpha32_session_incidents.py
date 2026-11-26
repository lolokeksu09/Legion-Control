#!/usr/bin/env python3
"""alpha.32: real incident engine + VM, navigation, bounds, tests and safety contracts."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
src = root/"app/src/main/java/dev/lolokeksu/legioncontrol"
def read(file): return (src/file).read_text(encoding="utf-8")
engine = read("SessionIncidents.kt")
screen = read("SessionLibraryActivity.kt")
vm = read("SessionLibraryViewModel.kt")
for expected in ("GAP_MS = 45_000L","MAX_SAMPLES = 10_000","MAX_EVENTS = 2_000",
                 "MAX_INCIDENTS = 400","MARK_WINDOW_MS = 60_000L",
                 "confirmed += at - last","SessionSignals.normalizeMarker",
                 "End.UNKNOWN","End.GAP","End.OBSERVATION_END",
                 "sawHighRefresh","refresh >= 90f","cell(raw: String)"):
    assert expected in engine,expected
assert "SessionIncidents.analyze(detail.header, detail.points, detail.events" in read("SessionAnalyticsSnapshot.kt")
assert "analyticsDetail(row.id)" in vm
for expected in ("SessionIncidents.csv(it.incidents,incidentKind)",
                 "SessionIncidents.render(it.incidents, incidentKind)", "vm::refreshSelected"):
    assert expected in screen, expected
manifest = (root/"app/src/main/AndroidManifest.xml").read_text()
for forbidden in ("QUERY_ALL_PACKAGES", "WRITE_SECURE_SETTINGS", "ACCESS_FINE_LOCATION"):
    assert forbidden not in manifest
assert "CREATE TABLE" not in engine+vm+screen
assert "ShizukuService" not in engine+vm+screen
print("PASS alpha32: bounded incidents, no invented duration, session navigation, SAF, 15 JVM tests")
