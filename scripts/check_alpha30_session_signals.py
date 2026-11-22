#!/usr/bin/env python3
"""Alpha.30: new event intelligence is real, local-only, user-triggered and bounded."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
src=r/"app/src/main/java/dev/lolokeksu/legioncontrol"
def read(name):return (src/name).read_text(encoding="utf-8")
engine=read("SessionSignals.kt")
service=read("GameSessionRecorderService.kt")
repo=read("GameSessionRepository.kt")
frag=read("SessionSignalsFragment.kt")
vm=read("SessionSignalsViewModel.kt")
game=read("GameSessionFragment.kt")
host=read("NavigationHostActivity.kt")
hub=read("HubPageFragment.kt")
test=(r/"app/src/test/java/dev/lolokeksu/legioncontrol/SessionSignalsTest.kt").read_text()
graph=ET.parse(r/"app/src/main/res/navigation/legion_nav_graph.xml").getroot()
android="{http://schemas.android.com/apk/res/android}"
nodes={n.get(android+"id"):n for n in graph.findall("fragment")}
assert "@+id/sessionSignalsFragment" in nodes
assert nodes["@+id/sessionSignalsFragment"].get(android+"name").endswith(".SessionSignalsFragment")
assert "R.id.sessionSignalsFragment" in host+hub+game
assert "NavigationHostActivity::class.java" in game
for term in ("ACTION_MARK", "USER_MARK", "SessionSignals.normalizeMarker",
             'if (!started || stopping)', 'stopSelfResult(startId)'):
    assert term in service,term
assert "startForegroundService(i)" in service and "ACTION_START" in service
assert 'context.startService(Intent(context,GameSessionRecorderService::class.java)' in service
assert "eventsChronological" in repo and "ORDER BY recorded_at_ms ASC,id ASC LIMIT ?" in repo
assert "GameSessionRepository(getApplication())" in vm
assert "repo.points(header.id,SessionSignals.MAX_SAMPLES)" in vm
assert "repo.eventsChronological(header.id,SessionSignals.MAX_EVENTS)" in vm
assert "Executors.newSingleThreadExecutor()" in vm and "onCleared()" in vm
assert "Intent.ACTION_CREATE_DOCUMENT" in frag and "vm.export(it)" in frag
assert "setTextIsSelectable(true)" in frag
assert "sampleLimitHit" in engine and "eventLimitHit" in engine
for token in ("SAMPLE_GAP","THERMAL_SEVERE","BATTERY_TEMP_HIGH","POWER_SAVING",
              "NETWORK_UNVALIDATED","NETWORK_TRANSPORT","DISPLAY_REFRESH",
              "MAX_SAMPLES=10_000","MAX_EVENTS=2_000","MAX_SIGNALS=600",
              "csvCell","normalizeMarker"):
    assert token in engine,token
assert test.count("@Test")>=7
manifest=(r/"app/src/main/AndroidManifest.xml").read_text()
for prohibited in ("QUERY_ALL_PACKAGES","WRITE_SECURE_SETTINGS","ACCESS_FINE_LOCATION"):
    assert prohibited not in manifest
for forbidden in ("Shizuku", "Runtime.getRuntime().exec", "HttpURLConnection", "startForegroundService"):
    assert forbidden not in vm+frag+engine,forbidden
print("PASS alpha30: local SQLite session events, manual marks, gap-aware engine, SAF and 7+ JVM tests")
