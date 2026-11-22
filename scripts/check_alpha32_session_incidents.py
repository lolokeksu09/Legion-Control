#!/usr/bin/env python3
"""alpha.32: real incident engine + VM, navigation, bounds, tests and safety contracts."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
src = root/"app/src/main/java/dev/lolokeksu/legioncontrol"
def read(file): return (src/file).read_text(encoding="utf-8")
engine = read("SessionIncidents.kt")
screen = read("SessionIncidentsFragment.kt")
vm = read("SessionIncidentsViewModel.kt")
hub = read("HubPageFragment.kt")
session = read("GameSessionFragment.kt")
host = read("NavigationHostActivity.kt")
signals = read("SessionSignalsFragment.kt")
baselines = read("SessionBaselinesFragment.kt")
test = (root/"app/src/test/java/dev/lolokeksu/legioncontrol/SessionIncidentsTest.kt").read_text()
nav = ET.parse(root/"app/src/main/res/navigation/legion_nav_graph.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
frags = {x.get(android+"id"):x for x in nav.findall("fragment")}
assert len(frags) == 19
node = frags["@+id/sessionIncidentsFragment"]
assert node.get(android+"name") == "dev.lolokeksu.legioncontrol.SessionIncidentsFragment"
assert [x.get(android+"name") for x in node.findall("argument")] == ["smart_package","session_id"]
for expected in ("GAP_MS = 45_000L","MAX_SAMPLES = 10_000","MAX_EVENTS = 2_000",
                 "MAX_INCIDENTS = 400","MARK_WINDOW_MS = 60_000L",
                 "confirmed += at - last","SessionSignals.normalizeMarker",
                 "End.UNKNOWN","End.GAP","End.OBSERVATION_END",
                 "sawHighRefresh","refresh >= 90f","cell(raw: String)"):
    assert expected in engine,expected
for expected in ("GameSessionRepository(getApplication())","repo.points(header.id, SessionIncidents.MAX_SAMPLES)",
                 "repo.eventsChronological(header.id, SessionIncidents.MAX_EVENTS)",
                 "Executors.newSingleThreadExecutor()","onCleared()","lastReport","SessionIncidents.csv(snapshot, filter)"):
    assert expected in vm,expected
for expected in ("Intent.ACTION_CREATE_DOCUMENT","vm.exportCsv(it)","vm.pickSessions()",
                 "vm.selectKind(","NavHostFragment.findNavController(this)"):
    assert expected in screen,expected
for expected in ("R.id.sessionIncidentsFragment",):
    assert expected in hub and expected in session and expected in host
    assert expected in signals and expected in baselines
assert test.count("@Test ") >= 15
manifest = (root/"app/src/main/AndroidManifest.xml").read_text()
for forbidden in ("QUERY_ALL_PACKAGES", "WRITE_SECURE_SETTINGS", "ACCESS_FINE_LOCATION"):
    assert forbidden not in manifest
assert "CREATE TABLE" not in engine+vm+screen
assert "ShizukuService" not in engine+vm+screen
print("PASS alpha32: bounded incidents, no invented duration, session navigation, SAF, 15 JVM tests")
