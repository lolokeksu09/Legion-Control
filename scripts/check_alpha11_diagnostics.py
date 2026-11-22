"""Run lossless v1/v2/v3→v4 upgrades against real SQLite, preserving legacy rows."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
store = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
sensor = (src / "SensorCaptureBackend.kt").read_text(encoding="utf-8")
schema2 = (src / "DiagnosticHistoryModels.kt").read_text(encoding="utf-8")
schema3 = (src / "SensorHistorySchema.kt").read_text(encoding="utf-8")
schema4 = (src / "SensorDiagnosticsSchema.kt").read_text(encoding="utf-8")

def table(model):
    found = re.search(r'const val CREATE_TABLE = """(.*?)"""', model, re.S)
    assert found, "missing executable SQLite table DDL"
    return found.group(1).strip()

def index(model):
    found = re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"', model)
    assert found, "missing executable SQLite index DDL"
    return found.group(1)

legacy_tables = re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""', store, re.S)
assert len(legacy_tables) == 2

for version in (1, 2, 3):
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    with db:
        for sql in legacy_tables:
            db.execute(sql)
        db.execute("CREATE INDEX idx_samples_session ON samples(session_id,recorded_at_ms)")
        db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('past',1000)")
        db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,percent,
            temperature_deci_c,charging_status,charging_source)
            VALUES('past',2000,1500,44,430,'Разряжается','Не подключена')""")
        if version >= 2:
            db.execute(table(schema2))
            db.execute(index(schema2))
            db.execute("""INSERT INTO diagnostic_samples
                (session_id,elapsed_ms,recorded_at_ms,thermal_status,thermal_headroom)
                VALUES('past',1500,2000,3,1.03)""")
        if version >= 3:
            db.execute(table(schema3))
            db.execute(index(schema3))
            db.execute("""INSERT INTO sensor_samples(capture_id,kind,recorded_at_ms,
                event_timestamp_ns,accuracy,x,y,z)
                VALUES('old-capture',1,2000,1000000,3,1.1,2.2,3.3)""")
        db.execute("PRAGMA user_version=" + str(version))
    old_samples = list(db.execute("SELECT * FROM samples"))
    old_sessions = list(db.execute("SELECT * FROM sessions"))
    old_diagnostics = (
        list(db.execute("SELECT * FROM diagnostic_samples")) if version >= 2 else []
    )
    old_sensors = list(db.execute("SELECT * FROM sensor_samples")) if version >= 3 else []

    with db:
        if version == 1:
            db.execute(table(schema2))
            db.execute(index(schema2))
        if version <= 2:
            db.execute(table(schema3))
            db.execute(index(schema3))
        db.execute(table(schema4))
        db.execute(index(schema4))
        db.execute("PRAGMA user_version=4")

    assert list(db.execute("SELECT * FROM samples")) == old_samples
    assert list(db.execute("SELECT * FROM sessions")) == old_sessions
    assert list(db.execute("SELECT * FROM diagnostic_samples")) == old_diagnostics
    assert list(db.execute("SELECT * FROM sensor_samples")) == old_sensors
    assert db.execute("PRAGMA user_version").fetchone()[0] == 4

    insert = """INSERT INTO sensor_probe_attempts(capture_id,kind,recorded_at_ms,state,
        event_count,rejected_events,latency_ms,timeout_ms,reporting_mode,rejection_reason)
        VALUES(?,?,?,?,?,?,?,?,?,?)"""
    db.execute(insert, ("c1",5,4000,"TIMEOUT",0,0,None,6000,1,None))
    db.execute(insert, ("c2",4,4200,"INVALID_DATA",3,3,None,3000,0,"STALE_EVENT"))
    db.execute("INSERT OR IGNORE INTO sensor_probe_attempts(capture_id,kind,recorded_at_ms,state,event_count,rejected_events,timeout_ms) VALUES('c1',5,5000,'TIMEOUT',0,0,6000)")
    assert db.execute("SELECT COUNT(*) FROM sensor_probe_attempts").fetchone()[0] == 2
    for bad in (
        ("bad",5,4300,"TIMEOUT",0,1,None,6000,1,None),
        ("bad",5,4300,"FICTIONAL",0,0,None,6000,1,None),
        ("bad",99,4300,"TIMEOUT",0,0,None,6000,1,None),
    ):
        try:
            db.execute(insert,bad)
        except sqlite3.IntegrityError:
            continue
        raise AssertionError("SQLite accepted invalid sensor attempt")
    # The v2 history is real data, not new thermal polling.
    assert db.execute("SELECT COUNT(*) FROM samples").fetchone()[0] == 1
    db.close()

assert "newVersion != ConnectivitySchema.VERSION" in store
assert "if (oldVersion == 1) installDiagnostics(db)" in store
assert "if (oldVersion <= 2) installSensors(db)" in store
assert "if (oldVersion <= 3) installSensorAttempts(db)" in store
assert "installSensorAttempts(db)" in store
assert "recordSensorCapture(capture: SensorCaptureResult)" in store
assert "db.beginTransaction()" in store and "db.setTransactionSuccessful()" in store
assert "history.recordSensorCapture(outcome)" in activity
assert "history.lastSensorAttempts()" in activity
assert "history.recentThermalReadings()" in activity
assert "LEFT JOIN diagnostic_samples AS d" in store
assert "SensorCapturePolicy.LIGHT_EXTENSION_MS" in sensor
assert "SensorSampleValidation.rejectionReason(" in sensor
assert "manager?.unregisterListener(this)" in sensor
assert "DROP TABLE" not in store and "DELETE FROM samples" not in store
print("PASS: real SQLite v1/v2/v3→v4 migrations and timeout/failure preservation")
