"""Alpha.4 static contracts for privacy, local persistence and explicit CSV export."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
store = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
sensors = (src / "AndroidSensorInventory.kt").read_text(encoding="utf-8")
csv = (src / "CsvReport.kt").read_text(encoding="utf-8")
models = (src / "HistoryModels.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/HistoryFormatTest.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()

assert set(x.attrib["{http://schemas.android.com/apk/res/android}name"] for x in manifest.findall("uses-permission")) == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS"
}, "History must not require Android storage or sensor permissions"
assert 'android:allowBackup="false"' in (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

for expected in (
    "SQLiteOpenHelper(", "legion-control-history.db", "CREATE TABLE sessions",
    "CREATE TABLE samples", "db.beginTransaction()", "db.setTransactionSuccessful()",
    "db.endTransaction()", "UNIQUE(session_id, elapsed_ms)", "fun recentSessions(",
    "fun writeCsv(writer: Writer)", "CsvReport.appendExtended(",
    "fun endSession(", 'throw IllegalStateException("No safe migration',
):
    assert expected in store, expected
assert "DROP TABLE" not in store and "DELETE FROM samples" not in store
assert "object ActiveSession" in models
assert "java.io.Writer" in csv
assert "Instant.ofEpochMilli" in csv
assert 'value.replace("\\\"", "\\\"\\\"")' in csv
assert "RussianQuantity.measurements(" in activity
assert "fun russianMeasurementForms()" in tests
assert "fun csvContainsExactValuesAndEscapesMultilineQuotes()" in tests
assert "fun missingMeasurementsUseEmptyColumnsNotFabricatedZero()" in tests
assert 'testImplementation("junit:junit:4.13.2")' in gradle

for expected in (
    "Intent.ACTION_CREATE_DOCUMENT", "Intent.CATEGORY_OPENABLE",
    "onActivityResult(", "openOutputStream(uri,",
    "history.writeCsv(writer)", "history.record(StoredReading(",
    "history.recentSessions()", "history.endSession(",
    "history.count()", "latestReport = null",
    "showSavedHistory()", "AndroidSensorInventory.inspect(this)",
    "setPositiveButton(\"Завершить\")", "engine.resetSession()",
):
    assert expected in activity, expected
# No startup sample: manual measurement only (also no passive sensor listeners).
assert "showSavedHistory()" in activity and "private fun manualSample()" in activity
on_create = activity.split("override fun onCreate(", 1)[1].split("override fun onStart()", 1)[0]
assert "sample()" not in on_create, "No battery capture on Activity startup"
assert "SensorManager" in sensors and "getDefaultSensor(type)" in sensors
assert "registerListener" not in sensors
assert 'versionCode = 16' in gradle
assert 'versionName = "0.1.0-alpha.16"' in gradle

for risky in ("WRITE_SECURE_SETTINGS", "QUERY_ALL_PACKAGES", "ACCESS_NETWORK_STATE",
              "INTERNET", "setprop ", "moto_extend_service", "pointer_speed"):
    assert risky not in activity + store + sensors + csv
print("PASS: alpha.4 SQLite on-device sessions, user-initiated CSV, safe sensor discovery and quantity tests")
