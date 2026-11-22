"""Real SQLite v1-v4 -> v5 data preservation and foreground battery test contracts."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
store = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
schema2 = (src / "DiagnosticHistoryModels.kt").read_text(encoding="utf-8")
schema3 = (src / "SensorHistorySchema.kt").read_text(encoding="utf-8")
schema4 = (src / "SensorDiagnosticsSchema.kt").read_text(encoding="utf-8")
schema5 = (src / "TimedBatterySchema.kt").read_text(encoding="utf-8")
models = (src / "TimedBatteryTestModels.kt").read_text(encoding="utf-8")
runner = (src / "ForegroundBatteryTest.kt").read_text(encoding="utf-8")
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
archive = (src / "DiagnosticArchive.kt").read_text(encoding="utf-8")

def table(model):
    found = re.search(r'const val CREATE_TABLE\s*=\s*"""(.*?)"""', model, re.S)
    assert found, "missing table DDL"
    return found.group(1).strip()

def index(model):
    found = re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"', model)
    assert found, "missing index DDL"
    return found.group(1)

legacy = re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""', store, re.S)
assert len(legacy) == 2

for old_version in (1, 2, 3, 4):
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    with db:
        for ddl in legacy:
            db.execute(ddl)
        db.execute("CREATE INDEX idx_samples_session ON samples(session_id,recorded_at_ms)")
        db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('legacy',1000)")
        db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,
            percent,temperature_deci_c,charging_status,charging_source)
            VALUES('legacy',2000,1500,40,410,'Разряжается','Не подключена')""")
        if old_version >= 2:
            db.execute(table(schema2))
            db.execute(index(schema2))
            db.execute("""INSERT INTO diagnostic_samples(session_id,elapsed_ms,
                recorded_at_ms,thermal_status,thermal_headroom)
                VALUES('legacy',1500,2000,3,1.03)""")
        if old_version >= 3:
            db.execute(table(schema3))
            db.execute(index(schema3))
            db.execute("""INSERT INTO sensor_samples(capture_id,kind,recorded_at_ms,
                event_timestamp_ns,accuracy,x,y,z)
                VALUES('c',1,2000,1111111,3,1,2,3)""")
        if old_version >= 4:
            db.execute(table(schema4))
            db.execute(index(schema4))
            db.execute("""INSERT INTO sensor_probe_attempts(capture_id,kind,
                recorded_at_ms,state,event_count,rejected_events,timeout_ms)
                VALUES('c',1,2000,'CAPTURED',1,0,3000)""")
        db.execute("PRAGMA user_version=" + str(old_version))
    snapshot = {name: list(db.execute("SELECT * FROM "+name)) for name in (
        ["sessions","samples"] +
        (["diagnostic_samples"] if old_version >= 2 else []) +
        (["sensor_samples"] if old_version >= 3 else []) +
        (["sensor_probe_attempts"] if old_version >= 4 else [])
    )}
    with db:
        if old_version == 1:
            db.execute(table(schema2))
            db.execute(index(schema2))
        if old_version <= 2:
            db.execute(table(schema3))
            db.execute(index(schema3))
        if old_version <= 3:
            db.execute(table(schema4))
            db.execute(index(schema4))
        db.execute(table(schema5))
        db.execute(index(schema5))
        db.execute("PRAGMA user_version=5")
    for name, values in snapshot.items():
        assert list(db.execute("SELECT * FROM "+name)) == values, (old_version,name)
    assert db.execute("PRAGMA user_version").fetchone()[0] == 5
    sql = """INSERT INTO timed_battery_tests(test_id,session_id,started_at_ms,
        finished_at_ms,reason,attempted_reads,successful_reads) VALUES(?,?,?,?,?,?,?)"""
    db.execute(sql, ("run", "legacy", 1000, 2000, "COMPLETED", 2, 1))
    db.execute("""INSERT OR IGNORE INTO timed_battery_tests(test_id,session_id,
        started_at_ms,finished_at_ms,reason,attempted_reads,successful_reads)
        VALUES('run','legacy',1000,2000,'COMPLETED',2,1)""")
    assert db.execute("SELECT COUNT(*) FROM timed_battery_tests").fetchone()[0] == 1
    for bad in (
        ("bad", "missing-session", 1000, 2000, "COMPLETED", 1, 0),
        ("bad", "legacy", 1000, 2000, "NOT_REAL", 1, 0),
        ("bad", "legacy", 1000, 2000, "COMPLETED", 1, 2),
        ("bad", "legacy", 3000, 2000, "COMPLETED", 1, 1),
    ):
        try:
            db.execute(sql, bad)
        except sqlite3.IntegrityError:
            continue
        raise AssertionError("Invalid timed test row accepted: "+str(bad))
    db.close()

for expected in (
    "newVersion != TimedBatterySchema.VERSION", "oldVersion !in 1..4",
    "if (oldVersion == 1) installDiagnostics(db)",
    "if (oldVersion <= 2) installSensors(db)",
    "if (oldVersion <= 3) installSensorAttempts(db)",
    "installTimedBatteryTests(db)",
    "fun recordTimedBatteryTest(test: TimedBatteryTestResult)",
    "fun recentTimedBatteryTests(limit: Int = 12)",
    "writeTimedBatteryTestsCsv(writer: Writer)",
):
    assert expected in store, expected
for expected in (
    "TimedBatteryPolicy.nextDelayMs(", "handler.removeCallbacks(tickRunnable)",
    "fun stop(reason: TimedBatteryStopReason)",
):
    assert expected in runner, expected
for expected in (
    "DURATION_MS = 60_000L", "INTERVAL_MS = 10_000L",
    "HOT_TEMPERATURE_DECI_C = 450", "TimedBatteryStopReason.THERMAL_SEVERE",
):
    assert expected in models, expected
for expected in (
    "timedTest?.stop(TimedBatteryStopReason.APP_BACKGROUND)",
    "history.recordTimedBatteryTest(completed)",
    "TimedBatteryPoint(", "private fun sample(): TimedBatteryPoint?",
    "timedTest?.isActive == true", "history.recentTimedBatteryTests()",
):
    assert expected in activity, expected
assert "timed-tests.csv" in archive
assert "timedCsv = ::writeTimedBatteryTestsCsv" in store
assert "DROP TABLE" not in store and "DELETE FROM samples" not in store
assert "Shizuku" not in runner and "registerReceiver" not in runner
print("PASS: v1/v2/v3/v4 -> v5 additive migration, timed session constraints and lifecycle wiring")
