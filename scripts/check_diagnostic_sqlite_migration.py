"""Exercise the actual v1 and v2 DDL against real SQLite, without deleting old data."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/dev/lolokeksu/legioncontrol/LocalHistoryStore.kt").read_text(encoding="utf-8")
models = (root / "app/src/main/java/dev/lolokeksu/legioncontrol/DiagnosticHistoryModels.kt").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/dev/lolokeksu/legioncontrol/MainActivity.kt").read_text(encoding="utf-8")
controller = (root / "app/src/main/java/dev/lolokeksu/legioncontrol/RustSampleController.kt").read_text(encoding="utf-8")
checks = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/DiagnosticSessionSummaryTest.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")

v1 = re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""', source, re.S)
assert len(v1) == 2, "Cannot locate both original v1 SQL table definitions"
v2 = re.search(r'const val CREATE_TABLE = """(.*?)"""', models, re.S)
index = re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"', models)
assert v2 and index, "Cannot locate the executable v2 migration SQL"

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys = ON")
with db:
    for statement in v1:
        db.execute(statement)
    db.execute("CREATE INDEX idx_samples_session ON samples(session_id, recorded_at_ms)")
    db.execute(
        "INSERT INTO sessions(id,started_at_ms,ended_at_ms) VALUES(?,?,NULL)",
        ("previous-session", 1000),
    )
    db.execute(
        """INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,percent,
           temperature_deci_c,charging_status,charging_source)
           VALUES(?,?,?,?,?,?,?)""",
        ("previous-session", 1100, 900, 53, 390, "Заряжается", "Сеть"),
    )
    db.execute("PRAGMA user_version=1")

before = list(db.execute("SELECT id,session_id,percent,temperature_deci_c FROM samples"))
old_sessions = list(db.execute("SELECT id,started_at_ms FROM sessions"))
with db:
    db.execute(v2.group(1).strip())
    db.execute(index.group(1))
    db.execute("PRAGMA user_version=2")

assert list(db.execute("SELECT id,session_id,percent,temperature_deci_c FROM samples")) == before
assert list(db.execute("SELECT id,started_at_ms FROM sessions")) == old_sessions
assert db.execute("PRAGMA user_version").fetchone()[0] == 2

db.execute(
    """INSERT INTO diagnostic_samples(session_id,elapsed_ms,recorded_at_ms,
       battery_percent,battery_temperature_deci_c,thermal_status,thermal_headroom,
       charge_counter_micro_ah) VALUES(?,?,?,?,?,?,?,?)""",
    ("previous-session", 900, 1100, 53, 390, 2, 0.98, 4210331),
)
assert db.execute("SELECT COUNT(*) FROM diagnostic_samples").fetchone()[0] == 1
assert db.execute("SELECT charge_counter_micro_ah FROM diagnostic_samples").fetchone()[0] == 4210331
assert db.execute("SELECT COUNT(*) FROM samples").fetchone()[0] == 1
db.execute(
    """INSERT OR IGNORE INTO diagnostic_samples(session_id,elapsed_ms,recorded_at_ms)
       VALUES(?,?,?)""",
    ("previous-session", 900, 1100),
)
assert db.execute("SELECT COUNT(*) FROM diagnostic_samples").fetchone()[0] == 1

def rejects(query, params):
    try:
        db.execute(query, params)
    except sqlite3.IntegrityError:
        return
    raise AssertionError("SQLite accepted invalid relation or out-of-range thermal code")

rejects(
    "INSERT INTO diagnostic_samples(session_id,elapsed_ms,recorded_at_ms) VALUES(?,?,?)",
    ("missing-session", 999, 1200),
)
db.execute(
    """INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,percent,
       temperature_deci_c,charging_status,charging_source) VALUES(?,?,?,?,?,?,?)""",
    ("previous-session", 2100, 1900, 55, 370, "Заряжается", "Сеть"),
)
rejects(
    """INSERT INTO diagnostic_samples(session_id,elapsed_ms,recorded_at_ms,thermal_status)
       VALUES(?,?,?,?)""",
    ("previous-session", 1900, 2100, 8),
)
assert "if (oldVersion == 1) installDiagnostics(db)" in source
assert "DiagnosticHistorySchema.CREATE_TABLE" in source
assert "DiagnosticHistorySchema.CREATE_INDEX" in source
assert 'throw IllegalStateException("No safe migration' in source
assert "DROP TABLE" not in source and "DELETE FROM samples" not in source
assert "writer.execute {" in controller
assert "sampleController.sample()" in activity
assert controller.index("history.recordDiagnostic(") > controller.index("writer.execute {")
assert "acceptedSessionId = diagnosticSessionId" in controller
assert "history.recordDiagnostic(" in controller
assert "history.recentDiagnostics(" in controller
assert "DiagnosticSessionAnalyzer.analyze(" in controller
assert "fun calculatesActualChargeRateFromSeveralManualMeasurements()" in checks
assert 'versionCode = 48' in gradle
assert 'versionName = "0.1.0-alpha.48"' in gradle
print("PASS: actual SQLite v1→v2 additive migration preserves rows, rejects orphans, validates diagnostics")
