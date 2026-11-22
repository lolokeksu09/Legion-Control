"""Exercise real analytics/archive SQLite SELECTs and check serialized Android wiring."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
store = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
archive = (src / "DiagnosticArchive.kt").read_text(encoding="utf-8")
sessions = (src / "SessionAnalytics.kt").read_text(encoding="utf-8")
sensors = (src / "SensorQualityAnalytics.kt").read_text(encoding="utf-8")
v2 = (src / "DiagnosticHistoryModels.kt").read_text(encoding="utf-8")
v3 = (src / "SensorHistorySchema.kt").read_text(encoding="utf-8")
v4 = (src / "SensorDiagnosticsSchema.kt").read_text(encoding="utf-8")

def ddl(source, key):
    result = re.search(r'const val ' + key + r'\s*=\s*"""(.*?)"""', source, re.S)
    assert result, "missing SQL definition " + key
    return result.group(1).strip()

def ix(source):
    result = re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"', source)
    assert result, "missing real SQLite index SQL"
    return result.group(1)

def sql_in(method):
    assert method in store, "missing real repository method: " + method
    part = store.split(method, 1)[1]
    found = re.search(r'"""(SELECT .*?)"""', part, re.S)
    assert found, "missing actual query for " + method
    return found.group(1).strip()

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
for legacy in re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""', store, re.S):
    db.execute(legacy)
db.execute("CREATE INDEX idx_samples_session ON samples(session_id,recorded_at_ms)")
for schema in (v2, v3, v4):
    db.execute(ddl(schema, "CREATE_TABLE"))
    db.execute(ix(schema))

db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('older',100)")
db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('latest',200)")
for (sid,ts,el,pct,temp,hz) in (
    ("older",101,100,80,310,60.0),
    ("latest",201,200,70,350,90.0),
    ("latest",301,300,66,410,120.0),
):
    db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,percent,
        temperature_deci_c,charging_status,charging_source,refresh_hz)
        VALUES(?,?,?,?,?,'Разряжается','Нет',?)""", (sid,ts,el,pct,temp,hz))
db.execute("""INSERT INTO diagnostic_samples(session_id,elapsed_ms,recorded_at_ms,
    thermal_status,thermal_headroom) VALUES('latest',300,301,3,1.03)""")
db.execute("""INSERT INTO sensor_samples(capture_id,kind,recorded_at_ms,
    event_timestamp_ns,accuracy,x,y,z) VALUES('c1',1,300,500,3,0.1,0.2,0.3)""")
for cid,kind,status,events,rejections,latency in (
    ("c1",1,"CAPTURED",1,0,40),
    ("c1",5,"TIMEOUT",0,0,None),
    ("c2",5,"INVALID_DATA",3,3,None),
):
    db.execute("""INSERT INTO sensor_probe_attempts(capture_id,kind,recorded_at_ms,state,
        event_count,rejected_events,latency_ms,timeout_ms)
        VALUES(?,?,?,?,?,?,?,6000)""", (cid,kind,303,status,events,rejections,latency))

latest = list(db.execute(sql_in("fun latestSessionReadings("), ("250",)))
assert len(latest) == 2, latest
assert [x[3] for x in latest] == [66,70], "latest user session only and newest-first"
count = db.execute(sql_in("fun latestSessionReadingCount(")).fetchone()[0]
assert count == 2
attempts = list(db.execute(sql_in("fun recentSensorAttempts("), ("400",)))
assert len(attempts) == 3
assert attempts[0][1] == "INVALID_DATA", attempts
assert db.execute(sql_in("private fun writeSensorAttemptsCsv(")).fetchall()[0][3] == "CAPTURED"
assert len(db.execute(sql_in("private fun writeSensorValuesCsv(")).fetchall()) == 1
thermal = db.execute(sql_in("private fun writeThermalHistoryCsv(")).fetchall()
assert len(thermal) == 3
assert thermal[-1][-2:] == (3,1.03)
assert thermal[0][-2:] == (None,None), "never coerce unknown history to zero"
assert "ZipOutputStream(output)" in archive
assert '"battery.csv"' in archive and '"sensor-values.csv"' in archive
assert "batteryCsv = ::writeCsv" in store, "battery CSV must reuse original exporter"
assert "fun analyze(readings: List<StoredReading>)" in sessions
assert "fun analyze(attempts: List<SavedSensorAttempt>)" in sensors

# The legacy battery write and child diagnostic insert are serialized in one worker job.
sample = activity.split("private fun sample(): TimedBatteryPoint? {", 1)[1]
assert sample.index("diagnosticsExecutor.execute {") < sample.index("history.record(StoredReading(")
assert sample.index("history.record(StoredReading(") < sample.index("history.recordDiagnostic(")
assert "acceptedSessionId == null || !primarySave.second" in sample
assert "history.endSession(" in activity
assert "history.writeDiagnosticArchive(it)" in activity
assert "REQUEST_EXPORT_ARCHIVE" in activity
assert "SensorQualityAnalytics.format(history.recentSensorAttempts())" in activity
assert "SessionAnalytics.analyze(points)" in activity
assert "diagnosticsExecutor.execute {" in activity.split("private fun showSavedHistory() {",1)[1].split("private fun showSensorQuality()",1)[0]
assert "DROP TABLE" not in store and "DELETE FROM samples" not in store
print("PASS: actual SQLite session, sensor and thermal queries; nonblocking export, archive and FK ordering")
