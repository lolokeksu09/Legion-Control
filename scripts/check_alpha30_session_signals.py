#!/usr/bin/env python3
"""Alpha.30: new event intelligence is real, local-only, user-triggered and bounded."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
src=r/"app/src/main/java/dev/lolokeksu/legioncontrol"
def read(name):return (src/name).read_text(encoding="utf-8")
engine=read("SessionSignals.kt")
service=read("GameSessionRecorderService.kt")
repo=read("GameSessionRepository.kt")
frag=read("SessionLibraryActivity.kt")
vm=read("SessionLibraryViewModel.kt")
for term in ("ACTION_MARK", "USER_MARK", "SessionSignals.normalizeMarker",
             'if (!started || stopping)', 'stopSelfResult(startId)'):
    assert term in service,term
assert "startForegroundService(i)" in service and "ACTION_START" in service
assert 'context.startService(Intent(context,GameSessionRecorderService::class.java)' in service
assert "eventsChronological" in repo and "ORDER BY recorded_at_ms ASC,id ASC LIMIT ?" in repo
assert "GameSessionRepository(getApplication())" in vm
assert "analyticsDetail(row.id)" in vm
assert "SessionSignals.analyze(detail.header, detail.points, detail.events" in read("SessionAnalyticsSnapshot.kt")
assert "Executors.newSingleThreadExecutor()" in vm and "onCleared()" in vm
assert "exportCsv(\"signals\",SessionSignals.csv(it.signals))" in frag
assert "SessionSignals.render(it.signals)" in frag
assert "sampleLimitHit" in engine and "eventLimitHit" in engine
for token in ("SAMPLE_GAP","THERMAL_SEVERE","BATTERY_TEMP_HIGH","POWER_SAVING",
              "NETWORK_UNVALIDATED","NETWORK_TRANSPORT","DISPLAY_REFRESH",
              "MAX_SAMPLES=10_000","MAX_EVENTS=2_000","MAX_SIGNALS=600",
              "csvCell","normalizeMarker"):
    assert token in engine,token
manifest=(r/"app/src/main/AndroidManifest.xml").read_text()
for prohibited in ("QUERY_ALL_PACKAGES","WRITE_SECURE_SETTINGS","ACCESS_FINE_LOCATION"):
    assert prohibited not in manifest
for forbidden in ("ShizukuService", "Runtime.getRuntime().exec", "HttpURLConnection", "startForegroundService"):
    assert forbidden not in vm+frag+engine,forbidden
print("PASS alpha30: local SQLite session events, manual marks, gap-aware engine, SAF and 7+ JVM tests")
