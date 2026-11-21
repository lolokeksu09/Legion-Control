"""Alpha.8: execute the CSV export JOIN against real v2 SQLite and old rows."""
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
store = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
schema = (src / "DiagnosticHistoryModels.kt").read_text(encoding="utf-8")
csv = (src / "CsvReport.kt").read_text(encoding="utf-8")
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/ExtendedCsvReportTest.kt").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()

legacy = re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""', store, re.S)
new_table = re.search(r'const val CREATE_TABLE = """(.*?)"""', schema, re.S)
export = re.search(
    r'fun writeCsv\(writer: Writer\): Int \{.*?val sql = """(.*?)"""\.trimIndent\(\)',
    store, re.S
)
assert len(legacy) == 2 and new_table and export, "Expected actual schema and export SQL"
assert "LEFT JOIN diagnostic_samples AS d" in export.group(1)
assert "d.session_id = s.session_id AND d.elapsed_ms = s.elapsed_ms" in export.group(1)
assert "ORDER BY s.recorded_at_ms ASC, s.id ASC" in export.group(1)

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
for ddl in legacy:
    db.execute(ddl)
db.execute(new_table.group(1))
db.execute("INSERT INTO sessions (id, started_at_ms) VALUES (?,?)", ("session", 100))
for elapsed, timestamp in [(200, 1000), (300, 2000)]:
    db.execute("""INSERT INTO samples(
        session_id,recorded_at_ms,elapsed_ms,percent,temperature_deci_c,
        charging_status,charging_source
    ) VALUES(?,?,?,?,?,?,?)""", ("session", timestamp, elapsed, 53, 390, "Заряжается", "Сеть"))
db.execute("""INSERT INTO diagnostic_samples(
    session_id, elapsed_ms, recorded_at_ms, battery_percent,
    battery_temperature_deci_c, thermal_status, thermal_headroom,
    charge_counter_micro_ah, current_now_micro_a, current_average_micro_a,
    ram_available_bytes
) VALUES(?,?,?,?,?,?,?,?,?,?,?)""", (
    "session", 300, 2100, 54, 370, 2, 0.92,
    5531816, 3260000, 3283000, 6000000000
))
rows = list(db.execute(export.group(1)))
assert len(rows) == 2, "LEFT JOIN duplicated or omitted legacy measurements"
assert all(len(row) == 24 for row in rows)
assert rows[0][0] == "session" and rows[0][2] == 200
assert all(value is None for value in rows[0][13:]), "Old v1 measurement must have NULL diagnostic fields"
assert rows[1][2] == 300
assert rows[1][13] == "session" and rows[1][14] == 300
assert rows[1][18] == 2 and abs(rows[1][19] - 0.92) < 1e-7
assert rows[1][20] == 5531816 and rows[1][21] == 3260000
assert rows[1][22] == 3283000 and rows[1][23] == 6000000000

assert 'CsvReport.beginExtended(writer)' in store
assert 'CsvReport.appendExtended(' in store
assert 'cursor.asDiagnosticStoredSampleOrNull()' in store
assert "if (isNull(13)) return null" in store
assert '"session_id", "timestamp_utc"' in csv, "Legacy CSV headers must stay first"
for name in (
    '"thermal_status"', '"thermal_headroom"', '"charge_counter_micro_ah"',
    '"current_now_micro_a"', '"current_average_micro_a"',
    '"ram_available_bytes"', '"diagnostic_timestamp_utc"',
):
    assert name in csv, name
assert 'diagnostic.sessionId == reading.sessionId' in csv
assert 'diagnostic.elapsedMs == reading.elapsedMs' in csv
assert 'history.writeCsv(writer)' in activity
assert "Intent.ACTION_CREATE_DOCUMENT" in activity
assert "Legion-Control-diagnostics-" in activity
assert "fun extendedCsvRetainsLegacyColumnsAndExportsActualDiagnostics()" in tests
assert "fun oldRowsHaveEmptyDiagnosticColumnsWithoutFabricatedZero()" in tests
assert "fun mismatchedDiagnosticCannotBeAttachedToWrongLegacyRow()" in tests
assert not manifest.findall("uses-permission")
assert 'versionCode = 11' in build
assert 'versionName = "0.1.0-alpha.11"' in build
assert "DROP TABLE" not in store and "DELETE FROM samples" not in store
print("PASS: extended 20-column export JOIN, v1 blank diagnostics, no row loss/duplicate, no privileges")
