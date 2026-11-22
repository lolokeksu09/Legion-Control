"""Alpha.19: real SQLite v7 -> v8 migration and experimental-data integrity."""
from pathlib import Path
import sqlite3
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
history=(src/"LocalHistoryStore.kt").read_text(encoding="utf-8")
schema=(src/"ExperimentSchema.kt").read_text(encoding="utf-8")
repository=(src/"ExperimentRepository.kt").read_text(encoding="utf-8")
analytics=(src/"ExperimentAnalytics.kt").read_text(encoding="utf-8")
activity=(src/"ExperimentLabActivity.kt").read_text(encoding="utf-8")
build=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionCode = 23' in build
assert 'versionName = "0.1.0-alpha.23"' in build
assert 'minSdk = 33' in build
assert 'const val VERSION = 8' in schema
assert 'newVersion != HapticLabSchema.VERSION' in history
assert 'oldVersion !in 1..11' in history
assert 'if (oldVersion <= 6) SmartProfilesSchema.install(db)' in history
assert 'if (oldVersion <= 5) GameSessionSchema.install(db)' in history
assert history.count('ExperimentSchema.install(db)')==2
assert "DROP TABLE" not in history and "DELETE FROM" not in history

manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
permissions={node.get(ns+"name") for node in manifest.findall("uses-permission")}
assert permissions=={
    "android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE","android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.POST_NOTIFICATIONS","android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.ACCESS_NOTIFICATION_POLICY","android.permission.WRITE_SETTINGS","android.permission.VIBRATE"
}
assert any(x.get(ns+"name")==".ExperimentLabActivity" and x.get(ns+"exported")=="false"
    for x in manifest.findall("./application/activity"))
assert "ExperimentLabActivity::class.java" in (src/"MainActivity.kt").read_text(encoding="utf-8")
assert 'count("layout.addView(Button(this).apply {")' not in activity

def ddl(text,expected):
    tables=re.findall(r'db.execSQL\("""(.*?)""".trimIndent\(\)\)',text,re.S)
    indexes=re.findall(r'db.execSQL\("(CREATE INDEX [^"]+)"\)',text)
    assert len(tables)==expected,len(tables)
    return tables,indexes

db=sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
oldTables=re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""',history,re.S)
assert len(oldTables)==2
for table in oldTables: db.execute(table)
db.execute("CREATE INDEX idx_samples_session ON samples(session_id,recorded_at_ms)")
for name in ("DiagnosticHistoryModels.kt","SensorHistorySchema.kt",
             "SensorDiagnosticsSchema.kt","TimedBatterySchema.kt"):
    source=(src/name).read_text(encoding="utf-8")
    table=re.search(r'const val CREATE_TABLE\s*=\s*"""(.*?)"""',source,re.S).group(1)
    index=re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"',source).group(1)
    db.execute(table);db.execute(index)
for name in ("GameSessionSchema.kt","SmartProfilesSchema.kt"):
    source=(src/name).read_text(encoding="utf-8")
    tables,indexes=ddl(source,3)
    for table in tables:db.execute(table)
    for index in indexes:db.execute(index)

db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('old',1000)")
db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,
    percent,temperature_deci_c,charging_status,charging_source)
    VALUES('old',2000,1000,75,350,'Discharging','None')""")
db.execute("""INSERT INTO game_sessions(session_id,package_name,app_label,started_at_ms,
    ended_at_ms,end_reason,tcp_opt_in) VALUES('oldgame','com.example.game','Game',
    1000,60000,'USER_STOP',0)""")
db.execute("""INSERT INTO game_samples(session_id,recorded_at_ms,battery_percent,network_type)
    VALUES('oldgame',2000,74,'WIFI')""")
db.execute("""INSERT INTO game_events(session_id,recorded_at_ms,event_type,details)
    VALUES('oldgame',3000,'NETWORK','ok')""")
db.execute("""INSERT INTO smart_profiles(package_name,label,created_at_ms,updated_at_ms,
    battery_hot_deci_c,battery_low_percent,tcp_opt_in)
    VALUES('com.example.game','Game',1000,1000,420,20,0)""")
db.execute("""INSERT INTO smart_rules(package_name,kind,threshold,cooldown_ms,enabled)
    VALUES('com.example.game','BATTERY_HOT',420,120000,1)""")
db.execute("""INSERT INTO smart_rule_events(session_id,package_name,kind,recorded_at_ms,details)
    VALUES('oldgame','com.example.game','BATTERY_HOT',4000,'42 degrees')""")
db.execute("PRAGMA user_version=7")
previous={name:list(db.execute("SELECT * FROM "+name)) for name in (
    "sessions","samples","game_sessions","game_samples","game_events",
    "smart_profiles","smart_rules","smart_rule_events")}
tables,indexes=ddl(schema,3)
with db:
    for table in tables:db.execute(table)
    for index in indexes:db.execute(index)
    db.execute("PRAGMA user_version=8")
assert db.execute("PRAGMA user_version").fetchone()[0]==8
for table,rows in previous.items():
    assert rows==list(db.execute("SELECT * FROM "+table)),table

db.execute("""INSERT INTO session_annotations(session_id,tag,note,updated_at_ms)
    VALUES('oldgame','Wi-Fi','Testing baseline',5000)""")
db.execute("""INSERT INTO session_experiments(experiment_id,package_name,name,hypothesis,
    label_a,label_b,created_at_ms) VALUES('ex1','com.example.game','Network','Test latency',
    'Wi-Fi','LTE',5000)""")
db.execute("""INSERT INTO session_experiment_members(experiment_id,session_id,arm,assigned_at_ms)
    VALUES('ex1','oldgame','A',5000)""")
for statement in (
    "INSERT INTO session_annotations(session_id,tag,note,updated_at_ms) VALUES('missing','a','b',5000)",
    "INSERT INTO session_annotations(session_id,tag,note,updated_at_ms) VALUES('oldgame','a','b',5000)",
    "INSERT INTO session_experiment_members(experiment_id,session_id,arm,assigned_at_ms) VALUES('ex1','oldgame','C',5000)",
    "INSERT INTO session_experiment_members(experiment_id,session_id,arm,assigned_at_ms) VALUES('ex1','missing','A',5000)",
    "INSERT INTO session_experiment_members(experiment_id,session_id,arm,assigned_at_ms) VALUES('missing','oldgame','A',5000)",
    "INSERT INTO session_experiment_members(experiment_id,session_id,arm,assigned_at_ms) VALUES('ex1','oldgame','A',5000)",
):
    try:db.execute(statement)
    except sqlite3.IntegrityError:pass
    else:raise AssertionError("Invalid SQL accepted: "+statement)

# Deletion cascades only to membership records, not historical game evidence/annotations.
db.execute("DELETE FROM session_experiments WHERE experiment_id='ex1'")
assert db.execute("SELECT COUNT(*) FROM session_experiment_members").fetchone()[0]==0
assert db.execute("SELECT note FROM session_annotations WHERE session_id='oldgame'").fetchone()[0]=="Testing baseline"
for table,rows in previous.items():
    assert rows==list(db.execute("SELECT * FROM "+table)),table
db.close()

for term in ("fun annotate(","fun create(","fun assign(","fun unassign(","fun exportCsv(",
             "fun recentSessions(","sessionPackage(db,sessionId)","db.beginTransaction()",
             "MAX_TRIALS","CONFLICT_REPLACE","qualityNotes","dischargePercentPerHour=null"):
    assert term in repository,term
for term in ("fun evidence(","fun armSummary(","fun formatComparison(","MIN_RATE_WINDOW_MS",
             "it.timeMs","gapsOver45s","qualityNotes","monotone"):
    assert term in analytics,term
for term in ("Intent.ACTION_CREATE_DOCUMENT","ExperimentRepository","fun confirmDelete()",
             "fun assign(","fun saveAnnotation()","fun showEvidence()","fun shareComparison()"):
    assert term in activity,term
print("PASS: real SQLite v7→v8 preservation, FK/cascade tests, A/B source integration and unchanged permissions")
