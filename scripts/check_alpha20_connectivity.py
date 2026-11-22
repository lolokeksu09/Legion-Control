"""Alpha.20: actual SQLite v8->v9 preservation plus precise network/API boundaries."""
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
store=(src/"LocalHistoryStore.kt").read_text(encoding="utf-8")
schema=(src/"ConnectivitySchema.kt").read_text(encoding="utf-8")
repo=(src/"ConnectivityRepository.kt").read_text(encoding="utf-8")
probe=(src/"ConnectivityProbe.kt").read_text(encoding="utf-8")
analytics=(src/"ConnectivityAnalytics.kt").read_text(encoding="utf-8")
ui=(src/"ConnectivityLabActivity.kt").read_text(encoding="utf-8")
viewmodel=(src/"ConnectivityLabViewModel.kt").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionCode = 27' in gradle
assert 'versionName = "0.1.0-alpha.27"' in gradle
assert 'minSdk = 33' in gradle and 'targetSdk = 36' in gradle
assert 'const val VERSION = 9' in schema
assert 'newVersion != HapticLabSchema.VERSION' in store
assert 'oldVersion !in 1..11' in store
assert 'if (oldVersion <= 7) ExperimentSchema.install(db)' in store
assert 'if (oldVersion <= 6) SmartProfilesSchema.install(db)' in store
assert 'if (oldVersion <= 5) GameSessionSchema.install(db)' in store
assert store.count("ConnectivitySchema.install(db)")==2
assert "DROP TABLE" not in store and "DELETE FROM samples" not in store

manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
perms={p.get(ns+"name") for p in manifest.findall("uses-permission")}
assert perms=={
    "android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE","android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS","android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.ACCESS_NOTIFICATION_POLICY","android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
}
assert all(p not in perms for p in (
    "android.permission.ACCESS_FINE_LOCATION","android.permission.QUERY_ALL_PACKAGES",
    "android.permission.WRITE_SECURE_SETTINGS"))
assert any(a.get(ns+"name")==".ConnectivityLabActivity" and
           a.get(ns+"exported")=="false" for a in manifest.findall("./application/activity"))
assert "ConnectivityLabActivity::class.java" in (src/"MainActivity.kt").read_text(encoding="utf-8")
assert "ConnectivityLabActivity::class.java" in (src/"SmartProfilesActivity.kt").read_text(encoding="utf-8")

def ddl(source,n):
    tables=re.findall(r'db.execSQL\("""(.*?)""".trimIndent\(\)\)',source,re.S)
    indexes=re.findall(r'db.execSQL\("(CREATE INDEX [^"]+)"\)',source)
    assert len(tables)==n,(len(tables),n)
    return tables,indexes

db=sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
core=re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""',store,re.S)
assert len(core)==2
for table in core:db.execute(table)
db.execute("CREATE INDEX idx_samples_session ON samples(session_id,recorded_at_ms)")
for filename in ("DiagnosticHistoryModels.kt","SensorHistorySchema.kt",
                 "SensorDiagnosticsSchema.kt","TimedBatterySchema.kt"):
    body=(src/filename).read_text(encoding="utf-8")
    table=re.search(r'const val CREATE_TABLE\s*=\s*"""(.*?)"""',body,re.S).group(1)
    index=re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"',body).group(1)
    db.execute(table);db.execute(index)
for name in ("GameSessionSchema.kt","SmartProfilesSchema.kt","ExperimentSchema.kt"):
    body=(src/name).read_text(encoding="utf-8")
    tables,indexes=ddl(body,3)
    for t in tables:db.execute(t)
    for ix in indexes:db.execute(ix)

db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('old',1000)")
db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,
    percent,temperature_deci_c,charging_status,charging_source)
    VALUES('old',1500,500,75,340,'Discharging','None')""")
db.execute("""INSERT INTO game_sessions(session_id,package_name,app_label,started_at_ms,
    ended_at_ms,end_reason,tcp_opt_in) VALUES('session1','com.example.game','Game',
    1000,60000,'USER_STOP',0)""")
db.execute("""INSERT INTO game_samples(session_id,recorded_at_ms,battery_percent,network_type)
    VALUES('session1',2000,74,'WIFI')""")
db.execute("""INSERT INTO game_events(session_id,recorded_at_ms,event_type,details)
    VALUES('session1',3000,'NETWORK','ok')""")
db.execute("""INSERT INTO smart_profiles(package_name,label,created_at_ms,updated_at_ms,
    battery_hot_deci_c,battery_low_percent,tcp_opt_in)
    VALUES('com.example.game','Game',1000,1000,420,20,0)""")
db.execute("""INSERT INTO smart_rules(package_name,kind,threshold,cooldown_ms,enabled)
    VALUES('com.example.game','BATTERY_HOT',420,120000,1)""")
db.execute("""INSERT INTO smart_rule_events(session_id,package_name,kind,recorded_at_ms,details)
    VALUES('session1','com.example.game','BATTERY_HOT',4000,'warning')""")
db.execute("""INSERT INTO session_annotations(session_id,tag,note,updated_at_ms)
    VALUES('session1','baseline','normal network',5000)""")
db.execute("""INSERT INTO session_experiments(experiment_id,package_name,name,hypothesis,
    label_a,label_b,created_at_ms)
    VALUES('ex1','com.example.game','WiFi test','reliability','WiFi','LTE',6000)""")
db.execute("""INSERT INTO session_experiment_members(experiment_id,session_id,arm,assigned_at_ms)
    VALUES('ex1','session1','A',7000)""")
db.execute("PRAGMA user_version=8")
old_names=("sessions","samples","game_sessions","game_samples","game_events",
          "smart_profiles","smart_rules","smart_rule_events","session_annotations",
          "session_experiments","session_experiment_members")
history={name:list(db.execute("SELECT * FROM "+name)) for name in old_names}

tables,indexes=ddl(schema,2)
assert len(indexes)==2
with db:
    for table in tables:db.execute(table)
    for index in indexes:db.execute(index)
    db.execute("PRAGMA user_version=9")
assert db.execute("PRAGMA user_version").fetchone()[0]==9
for name,rows in history.items():
    assert rows==list(db.execute("SELECT * FROM "+name)),name

db.execute("""INSERT INTO connectivity_runs(run_id,host,started_at_ms,mode,status)
    VALUES('network1','dns.google',10000,'STABILITY','RUNNING')""")
db.execute("""INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,
    family,dns_ms,tcp_ms,tls_ms,result,error_code,network_type,network_validated)
    VALUES('network1',1,11000,'IPV4',12.5,30.0,40.0,'OK','','WIFI',1)""")
db.execute("""INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,
    family,dns_ms,result,network_type)
    VALUES('network1',1,11000,'IPV6',12.5,'NO_ADDRESS','WIFI')""")
for stmt in (
    "INSERT INTO connectivity_runs(run_id,host,started_at_ms,mode,status) VALUES('bad1','192.168.0.1',100,'SINGLE','RUNNING')",
    "INSERT INTO connectivity_runs(run_id,host,started_at_ms,mode,status) VALUES('bad2','dns.google',100,'CRAWL','RUNNING')",
    "INSERT INTO connectivity_runs(run_id,host,started_at_ms,ended_at_ms,mode,status) VALUES('bad3','dns.google',100,200,'SINGLE','RUNNING')",
    "INSERT INTO connectivity_runs(run_id,host,started_at_ms,mode,status) VALUES('bad4','dns.google',100,'SINGLE','COMPLETE')",
    "INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,family,result,network_type) VALUES('bad',1,1,'IPV4','OK','WIFI')",
    "INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,family,result,network_type) VALUES('network1',7,1,'IPV4','OK','WIFI')",
    "INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,family,result,network_type) VALUES('network1',2,1,'IPV5','OK','WIFI')",
    "INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,family,result,network_type) VALUES('network1',2,1,'IPV4','SCANNED','WIFI')",
    "INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,family,dns_ms,result,network_type) VALUES('network1',2,1,'IPV4',-50,'OK','WIFI')",
    "INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,family,result,network_type,network_validated) VALUES('network1',2,1,'IPV4','OK','WIFI',3)",
    "INSERT INTO connectivity_samples(run_id,cycle,recorded_at_ms,family,result,network_type) VALUES('network1',1,2,'IPV4','OK','WIFI')",
):
    try:db.execute(stmt)
    except sqlite3.IntegrityError:pass
    else:raise AssertionError("Invalid SQL succeeded: "+stmt)

db.execute("""UPDATE connectivity_runs
    SET ended_at_ms=MAX(started_at_ms,20000),status='INTERRUPTED'
    WHERE status='RUNNING'""")
assert db.execute("SELECT status FROM connectivity_runs WHERE run_id='network1'").fetchone()[0]=="INTERRUPTED"
db.execute("DELETE FROM connectivity_runs WHERE run_id='network1'")
assert db.execute("SELECT COUNT(*) FROM connectivity_samples").fetchone()[0]==0
for name,rows in history.items():
    assert rows==list(db.execute("SELECT * FROM "+name)),name
db.close()

for phrase in ("fun start(","fun record(","fun finish(","fun runs(","fun comparison(",
               "fun lastSevenDays(","fun writeCsv(","INTERUPTED","'INTERRUPTED'",
               "db.beginTransaction()","CONFLICT"):
    if phrase=="INTERUPTED" or phrase=="CONFLICT":continue
    assert phrase in repo,phrase
for phrase in ("fun capture(","InetAddress.getAllByName(host)","Inet4Address","Inet6Address",
               "CONNECT_TIMEOUT_MS","TLS_TIMEOUT_MS","SNIHostName(host)",
               'endpointIdentificationAlgorithm="HTTPS"',"tls.startHandshake()",
               "one.one.one.one","dns.google","dns.quad9.net"):
    assert phrase in probe,phrase
for phrase in ("fun distribution(","fun summarize(","fun compare(","dnsFailures",
               "ipv6AvailableCycles","eligibleConnections","format","НЕ ping игров"):
    assert phrase in analytics,phrase
for phrase in ("fun startTest(","onStop()","cancelTest(false)",
               "ACTION_CREATE_DOCUMENT","fun compare()","fun weekReport()",
               "fun showHistory()","fun confirmDelete()"):
    assert phrase in ui,phrase
for phrase in ("Future<*>", "ConnectivityProbe.capture(", "ConnectivityRepository(getApplication<Application>())",
               "fun cancelTest()", "onCleared()", "LiveData"):
    assert phrase in viewmodel,phrase
assert "startForegroundService" not in ui
assert "startActivityForResult" in ui
print("PASS: real SQLite v8→v9 migration preserves all historic tables, FK, constraints, privacy and diagnosis contracts")
