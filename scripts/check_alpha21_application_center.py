"""Alpha.21 — executable real SQLite v9 -> v10 preservation, constraints and permission gates."""
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
store=(src/"LocalHistoryStore.kt").read_text(encoding="utf-8")
schema=(src/"ApplicationCenterSchema.kt").read_text(encoding="utf-8")
inventory=(src/"ApplicationInventory.kt").read_text(encoding="utf-8")
repository=(src/"ApplicationCenterRepository.kt").read_text(encoding="utf-8")
activity=(src/"ApplicationCenterFragment.kt").read_text(encoding="utf-8")
compat=(src/"ApplicationCenterActivity.kt").read_text(encoding="utf-8")
viewmodel=(src/"ApplicationCenterViewModel.kt").read_text(encoding="utf-8")
rules=(src/"ApplicationAuditRules.kt").read_text(encoding="utf-8")
usage=(src/"ApplicationUsageInspector.kt").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert "versionCode = 47" in gradle
assert 'versionName = "0.1.0-alpha.47"' in gradle
assert 'minSdk = 33' in gradle and 'targetSdk = 36' in gradle
assert "const val VERSION = 10" in schema
assert "newVersion != HapticLabSchema.VERSION" in store
assert "oldVersion !in 1..11" in store
assert "if (oldVersion <= 8) ConnectivitySchema.install(db)" in store
assert store.count("ApplicationCenterSchema.install(db)")==2
assert "DROP TABLE" not in store and "DELETE FROM samples" not in store

manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
perms={node.get(ns+"name") for node in manifest.findall("uses-permission")}
assert perms=={
    "android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE","android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS","android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY","android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
}
assert "android.permission.QUERY_ALL_PACKAGES" not in perms
assert "android.permission.ACCESS_FINE_LOCATION" not in perms
assert any(a.get(ns+"name")==".ApplicationCenterActivity" and
           a.get(ns+"exported")=="false" for a in manifest.findall("./application/activity"))
assert "ApplicationCenterActivity::class.java" in (src/"MainActivity.kt").read_text(encoding="utf-8")
assert "ApplicationCenterActivity::class.java" in (src/"SmartProfilesFragment.kt").read_text(encoding="utf-8")

def ddl(source,expected):
    tables=re.findall(r'db.execSQL\("""(.*?)""".trimIndent\(\)\)',source,re.S)
    indexes=re.findall(r'db.execSQL\("(CREATE INDEX [^"]+)"\)',source)
    assert len(tables)==expected,(len(tables),expected)
    return tables,indexes

db=sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
core=re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""',store,re.S)
assert len(core)==2
for sql in core:db.execute(sql)
db.execute("CREATE INDEX idx_samples_session ON samples(session_id,recorded_at_ms)")
for file in ("DiagnosticHistoryModels.kt","SensorHistorySchema.kt",
             "SensorDiagnosticsSchema.kt","TimedBatterySchema.kt"):
    text=(src/file).read_text(encoding="utf-8")
    db.execute(re.search(r'const val CREATE_TABLE\s*=\s*"""(.*?)"""',text,re.S).group(1))
    db.execute(re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"',text).group(1))
for file,expected in (
    ("GameSessionSchema.kt",3),("SmartProfilesSchema.kt",3),
    ("ExperimentSchema.kt",3),("ConnectivitySchema.kt",2)
):
    text=(src/file).read_text(encoding="utf-8")
    tables,indexes=ddl(text,expected)
    for sql in tables+indexes:db.execute(sql)

db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('old',1000)")
db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,
    percent,temperature_deci_c,charging_status,charging_source)
    VALUES('old',2000,1000,70,370,'Discharging','None')""")
db.execute("""INSERT INTO game_sessions(session_id,package_name,app_label,started_at_ms,
    ended_at_ms,end_reason,tcp_opt_in)
    VALUES('session','com.example.game','Game',1000,5000,'USER_STOP',0)""")
db.execute("""INSERT INTO game_samples(session_id,recorded_at_ms,battery_percent,network_type)
    VALUES('session',2000,70,'WIFI')""")
db.execute("""INSERT INTO game_events(session_id,recorded_at_ms,event_type,details)
    VALUES('session',2000,'TEST','old event')""")
db.execute("""INSERT INTO smart_profiles(package_name,label,created_at_ms,updated_at_ms,
    battery_hot_deci_c,battery_low_percent,tcp_opt_in)
    VALUES('com.example.game','Example',1000,1000,420,20,0)""")
db.execute("""INSERT INTO smart_rules(package_name,kind,threshold,cooldown_ms,enabled)
    VALUES('com.example.game','BATTERY_HOT',420,120000,1)""")
db.execute("""INSERT INTO smart_rule_events(session_id,package_name,kind,recorded_at_ms,details)
    VALUES('session','com.example.game','BATTERY_HOT',3000,'heat')""")
db.execute("""INSERT INTO session_annotations(session_id,tag,note,updated_at_ms)
    VALUES('session','test','old note',4000)""")
db.execute("""INSERT INTO session_experiments(experiment_id,package_name,name,hypothesis,label_a,label_b,created_at_ms)
    VALUES('exp','com.example.game','Experiment','','A','B',5000)""")
db.execute("""INSERT INTO session_experiment_members(experiment_id,session_id,arm,assigned_at_ms)
    VALUES('exp','session','A',5500)""")
db.execute("""INSERT INTO connectivity_runs(run_id,host,started_at_ms,ended_at_ms,mode,status)
    VALUES('net','dns.google',10000,20000,'SINGLE','COMPLETE')""")
db.execute("""INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,family,
    dns_ms,tcp_ms,tls_ms,result,network_type,network_validated)
    VALUES('net',1,11000,'IPV4',10.0,20.0,40.0,'OK','WIFI',1)""")
db.execute("PRAGMA user_version=9")
old_names=("sessions","samples","game_sessions","game_samples","game_events",
           "smart_profiles","smart_rules","smart_rule_events","session_annotations",
           "session_experiments","session_experiment_members","connectivity_runs",
           "connectivity_samples")
old={name:list(db.execute("SELECT * FROM "+name)) for name in old_names}

tables,indexes=ddl(schema,2)
assert len(indexes)==2
with db:
    for sql in tables+indexes:db.execute(sql)
    db.execute("PRAGMA user_version=10")
assert db.execute("PRAGMA user_version").fetchone()[0]==10
for name,records in old.items():
    assert list(db.execute("SELECT * FROM "+name))==records,name

db.execute("""INSERT INTO app_library(package_name,alias,category,note,favorite,
    created_at_ms,updated_at_ms,last_seen_status)
    VALUES('com.example.game','Game','MOBA','A note',1,10000,10000,'UNKNOWN')""")
db.execute("""INSERT INTO app_audit_events(package_name,recorded_at_ms,kind,details)
    VALUES('com.example.game',12000,'FIRST_OBSERVED','test')""")

for sql in (
    "INSERT INTO app_library(package_name,created_at_ms,updated_at_ms) VALUES('x',100,100)",
    "INSERT INTO app_library(package_name,created_at_ms,updated_at_ms) VALUES('com.example.other',100,50)",
    "INSERT INTO app_library(package_name,favorite,created_at_ms,updated_at_ms) VALUES('com.example.other',3,100,100)",
    "INSERT INTO app_library(package_name,last_seen_status,created_at_ms,updated_at_ms) VALUES('com.example.other','UNINSTALLED',100,100)",
    "INSERT INTO app_library(package_name,created_at_ms,updated_at_ms) VALUES('com.example.game',100,100)",
    "INSERT INTO app_audit_events(package_name,recorded_at_ms,kind) VALUES('com.missing.package',12000,'FIRST_OBSERVED')",
    "INSERT INTO app_audit_events(package_name,recorded_at_ms,kind) VALUES('com.example.game',12000,'FAKE_EVENT')",
    "INSERT INTO app_audit_events(package_name,recorded_at_ms,kind) VALUES('com.example.game',0,'VERSION_CHANGED')",
):
    try:db.execute(sql)
    except sqlite3.IntegrityError:continue
    raise AssertionError("Invalid SQL accepted: "+sql)

db.execute("DELETE FROM app_library WHERE package_name='com.example.game'")
assert db.execute("SELECT COUNT(*) FROM app_audit_events").fetchone()[0]==0
for name,records in old.items():
    assert list(db.execute("SELECT * FROM "+name))==records,name
db.close()

for phrase in ("fun save(","fun audit(","fun events(","fun writeCsv(","fun remove(",
               "db.beginTransaction()","last_seen_status",
               "MAX_ITEMS","csvCell"):
    assert phrase in repository,phrase
for phrase in ("fun launchable(","queryIntentActivities","GET_PERMISSIONS",
               "getPackageInfo","isIgnoringBatteryOptimizations",
               "ACTION_APPLICATION_DETAILS_SETTINGS","ACTION_APP_NOTIFICATION_SETTINGS",
               "ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS","getLaunchIntentForPackage"):
    assert phrase in inventory,phrase
for phrase in ("FIRST_OBSERVED","BECAME_UNAVAILABLE","VERSION_CHANGED",
               "НЕ доказательство удаления","fun classify(","fun csvCell("):
    assert phrase in rules,phrase
assert "queryAndAggregateUsageStats" in usage and "hasUsageAccess" in usage
for phrase in ("ACTION_CREATE_DOCUMENT","fun scanAll(","fun scanOne(","fun inspect(",
               "fun chooseTracked(","fun usage(","fun confirmRemove()"):
    assert phrase in activity,phrase
assert "startForegroundService" not in activity
assert "ModuleNavigation.forwardLegacyEntry(this)" in compat
assert "state.observe(viewLifecycleOwner)" in activity
assert "ViewModelProvider(this)[ApplicationCenterViewModel::class.java]" in activity
for term in ("ApplicationInventory.inspect(", "ApplicationCenterRepository(getApplication<Application>())",
             "visible.get()", "fun scanAll(", "exportCsv(uri", "onCleared()"):
    assert term in viewmodel,term
print("PASS alpha21: SQLite v9→v10 preserves all 13 previous tables; FK, checks and Android public-API contracts")
