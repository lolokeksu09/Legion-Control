"""Exercise actual v1/v2 -> v3 SQL migration on real SQLite without deleting old rows."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
store = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
v2model = (src / "DiagnosticHistoryModels.kt").read_text(encoding="utf-8")
v3model = (src / "SensorHistorySchema.kt").read_text(encoding="utf-8")
capture = (src / "SensorCaptureBackend.kt").read_text(encoding="utf-8")
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")

v1 = re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""', store, re.S)
v2 = re.search(r'const val CREATE_TABLE = """(.*?)"""', v2model, re.S)
v2index = re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"', v2model)
v3 = re.search(r'const val CREATE_TABLE = """(.*?)"""', v3model, re.S)
v3index = re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"', v3model)
assert len(v1) == 2 and v2 and v2index and v3 and v3index

for original_version in (1, 2):
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    with db:
        for statement in v1:
            db.execute(statement)
        db.execute("CREATE INDEX idx_samples_session ON samples(session_id, recorded_at_ms)")
        db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('legacy',1000)")
        db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,percent,
            temperature_deci_c,charging_status,charging_source)
            VALUES('legacy',1100,900,55,360,'Заряжается','USB')""")
        if original_version == 2:
            db.execute(v2.group(1).strip())
            db.execute(v2index.group(1))
            db.execute("""INSERT INTO diagnostic_samples(session_id,elapsed_ms,recorded_at_ms,
                thermal_status) VALUES('legacy',900,1100,2)""")
        db.execute("PRAGMA user_version=" + str(original_version))
    before = list(db.execute("SELECT * FROM samples"))
    old_sessions = list(db.execute("SELECT * FROM sessions"))
    old_diagnostics = list(db.execute("SELECT * FROM diagnostic_samples")) if original_version == 2 else []
    with db:
        if original_version == 1:
            db.execute(v2.group(1).strip())
            db.execute(v2index.group(1))
        db.execute(v3.group(1).strip())
        db.execute(v3index.group(1))
        db.execute("PRAGMA user_version=3")
    assert list(db.execute("SELECT * FROM samples")) == before
    assert list(db.execute("SELECT * FROM sessions")) == old_sessions
    assert list(db.execute("SELECT * FROM diagnostic_samples")) == old_diagnostics
    assert db.execute("PRAGMA user_version").fetchone()[0] == 3

    sql = """INSERT INTO sensor_samples(capture_id,kind,recorded_at_ms,
        event_timestamp_ns,accuracy,x,y,z) VALUES(?,?,?,?,?,?,?,?)"""
    db.execute(sql, ("capture-id", 1, 1200, 1000000000, 3, 1.2, 2.3, -0.4))
    db.execute("""INSERT OR IGNORE INTO sensor_samples(capture_id,kind,recorded_at_ms,
        event_timestamp_ns,accuracy,x) VALUES('capture-id',1,1250,1000000001,3,8)""")
    assert db.execute("SELECT COUNT(*) FROM sensor_samples").fetchone()[0] == 1
    for invalid in (
        ("bad", 99, 1200, 10000, 3, 1, None, None),
        ("bad", 5, 1200, 10000, 8, 1, None, None),
        ("bad", 5, -1, 10000, 3, 1, None, None),
    ):
        try:
            db.execute(sql, invalid)
            raise AssertionError("Invalid sensor reading accepted")
        except sqlite3.IntegrityError:
            pass
    db.close()

assert "oldVersion == 1 && newVersion == SensorHistorySchema.VERSION" in store
assert "oldVersion == 2 && newVersion == SensorHistorySchema.VERSION" in store
assert "installSensors(db)" in store
assert "history.recordSensorCapture(" in activity
assert "SensorCaptureBackend(this)" in activity
assert "SensorSampleValidation.accept(" in capture
assert "unregisterListener(this)" in capture
assert "postDelayed(timeout, 3000L)" in capture
assert "DROP TABLE" not in store and "DELETE FROM samples" not in store
print("PASS: real SQLite v1/v2->v3 migrations preserve previous data and validate sensor constraints")
