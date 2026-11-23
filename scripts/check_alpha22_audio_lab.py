"""Alpha.22 real SQLite v10→v11, preserving all historical data including app-library rows."""
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
store=(src/"LocalHistoryStore.kt").read_text(encoding="utf-8")
schema=(src/"AudioLabSchema.kt").read_text(encoding="utf-8")
signal=(src/"AudioSignalGenerator.kt").read_text(encoding="utf-8")
backend=(src/"AudioTestBackend.kt").read_text(encoding="utf-8")
repository=(src/"AudioLabRepository.kt").read_text(encoding="utf-8")
routing=(src/"AudioRouteInspector.kt").read_text(encoding="utf-8")
activity=(src/"AudioLabFragment.kt").read_text(encoding="utf-8")
compat=(src/"AudioLabActivity.kt").read_text(encoding="utf-8")
viewmodel=(src/"AudioLabViewModel.kt").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionCode = 40' in gradle and 'versionName = "0.1.0-alpha.40"' in gradle
assert 'minSdk = 33' in gradle and 'targetSdk = 36' in gradle
assert 'const val VERSION = 11' in schema
assert 'newVersion != HapticLabSchema.VERSION' in store and 'oldVersion !in 1..11' in store
assert 'if (oldVersion <= 9) ApplicationCenterSchema.install(db)' in store
assert store.count('AudioLabSchema.install(db)')==2
assert 'DROP TABLE' not in store and 'DELETE FROM samples' not in store

manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
permissions={n.get(ns+"name") for n in manifest.findall("uses-permission")}
assert permissions=={
    "android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE","android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS","android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY","android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
}
assert "android.permission.RECORD_AUDIO" in permissions
assert "android.permission.QUERY_ALL_PACKAGES" not in permissions
assert any(a.get(ns+"name")==".AudioLabActivity" and a.get(ns+"exported")=="false"
           for a in manifest.findall("./application/activity"))
assert 'AudioLabActivity::class.java' in (src/"MainActivity.kt").read_text(encoding="utf-8")

# Build an ACTUAL v10 SQLite database with legacy rows, not just a schema-name assertion.
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

tables,indexes=ddl((src/"ApplicationCenterSchema.kt").read_text(encoding="utf-8"),2)
for sql in tables+indexes:db.execute(sql)
db.execute("""INSERT INTO app_library(package_name,alias,category,note,favorite,
    created_at_ms,updated_at_ms,last_seen_status)
    VALUES('com.example.game','Game','MOBA','saved note',1,10000,10000,'VISIBLE')""")
db.execute("""INSERT INTO app_audit_events(package_name,recorded_at_ms,kind,details)
    VALUES('com.example.game',11000,'FIRST_OBSERVED','existing')""")
db.execute("PRAGMA user_version=10")
old_names=[row[0] for row in db.execute("""
    SELECT name FROM sqlite_master
    WHERE type='table' AND name NOT LIKE 'sqlite_%'
    ORDER BY name
""")]
assert len(old_names)>=15,old_names
previous={name:list(db.execute("SELECT * FROM "+name)) for name in old_names}

tables,indexes=ddl(schema,2)
assert len(indexes)==2
with db:
    for sql in tables+indexes: db.execute(sql)
    db.execute("PRAGMA user_version=11")
assert db.execute("PRAGMA user_version").fetchone()[0]==11
for name,records in previous.items():
    assert list(db.execute("SELECT * FROM "+name))==records,name

db.execute("""INSERT INTO audio_test_runs(run_id,started_at_ms,kind,channel,frequency_hz,
    duration_ms,sample_rate_hz,expected_frames,state)
    VALUES('audio1',20000,'TONE','LEFT',440,1500,48000,72000,'RUNNING')""")
db.execute("""INSERT INTO audio_route_events(run_id,recorded_at_ms,event_kind,output_device_type,details)
    VALUES('audio1',21000,'ROUTE_START',2,'speaker')""")
for sql in (
    "INSERT INTO audio_test_runs(run_id,started_at_ms,kind,channel,frequency_hz,duration_ms,sample_rate_hz,expected_frames,state) VALUES('bad1',1,'MIC','LEFT',440,1500,48000,72000,'RUNNING')",
    "INSERT INTO audio_test_runs(run_id,started_at_ms,kind,channel,frequency_hz,duration_ms,sample_rate_hz,expected_frames,state) VALUES('bad2',1,'TONE','NO_CHANNEL',440,1500,48000,72000,'RUNNING')",
    "INSERT INTO audio_test_runs(run_id,started_at_ms,kind,channel,frequency_hz,duration_ms,sample_rate_hz,expected_frames,state) VALUES('bad3',1,'TONE','LEFT',440,4000,48000,192000,'RUNNING')",
    "INSERT INTO audio_test_runs(run_id,started_at_ms,kind,channel,frequency_hz,duration_ms,sample_rate_hz,expected_frames,state) VALUES('bad4',1,'TONE','LEFT',440,1000,48000,48000,'COMPLETE')",
    "INSERT INTO audio_test_runs(run_id,started_at_ms,ended_at_ms,kind,channel,frequency_hz,duration_ms,sample_rate_hz,expected_frames,state) VALUES('bad5',1000,500,'TONE','LEFT',440,1000,48000,48000,'ERROR')",
    "INSERT INTO audio_route_events(run_id,recorded_at_ms,event_kind,details) VALUES('unknown',21000,'ROUTE_START','no parent')",
    "INSERT INTO audio_route_events(run_id,recorded_at_ms,event_kind,details) VALUES('audio1',21000,'MIC_RECORD','invalid')",
    "INSERT INTO audio_route_events(run_id,recorded_at_ms,event_kind,details) VALUES('audio1',0,'ROUTE_START','invalid')",
):
    try: db.execute(sql)
    except sqlite3.IntegrityError: pass
    else: raise AssertionError("Invalid SQL accepted: "+sql)
db.execute("""UPDATE audio_test_runs SET ended_at_ms=24000,state='COMPLETE',
    observed_frames=72000 WHERE run_id='audio1' AND state='RUNNING'""")
assert db.execute("SELECT state FROM audio_test_runs WHERE run_id='audio1'").fetchone()[0]=="COMPLETE"
db.execute("DELETE FROM audio_test_runs WHERE run_id='audio1'")
assert db.execute("SELECT COUNT(*) FROM audio_route_events").fetchone()[0]==0
for name,records in previous.items():
    assert list(db.execute("SELECT * FROM "+name))==records,name
columns={r[1].lower() for r in db.execute("PRAGMA table_info(audio_test_runs)")}
assert not any(word in col for col in columns for word in ("mic","pcm","recorded_audio","waveform"))
db.close()

for word in ("fun generate(","ShortArray","AMPLITUDE = 0.10",
             "SWEEP","CHANNEL_ID","fadeFrames","coerceIn","fun frames("):
    assert word in signal,word
for word in ("AudioTrack.Builder()","AudioTrack.MODE_STATIC","requestAudioFocus",
             "abandonAudioFocusRequest","stopFlag","onRoutingChangedListener","AudioPlaybackResult"):
    if word=="onRoutingChangedListener":word="addOnRoutingChangedListener"
    assert word in backend,word
for word in ("AudioDeviceCallback","getDevices","TYPE_BLUETOOTH_A2DP",
             "unregisterAudioDeviceCallback","STREAM_MUSIC"):
    assert word in routing,word
for word in ("fun start(","fun finish(","fun exportCsv(","db.beginTransaction()",
             "AudioRouteRecord","fun compareRecent(","fun delete("):
    assert word in repository,word
for word in ("override fun onStop()","viewModel.stopPlayback()","ACTION_CREATE_DOCUMENT",
             "fun pickHistory()","fun confirmDelete()","fun selectedPlay()"):
    assert word in activity,word
for word in ("backend.stop()", "backend.play(type,which,hz,length)", "AudioLabRepository(getApplication<Application>())",
             "AudioRouteMonitor(", "onCleared()", "LiveData"):
    assert word in viewmodel,word
assert "startForegroundService" not in activity and "MediaRecorder" not in activity
assert "AudioLabFragment()" in compat and "state.observe(viewLifecycleOwner)" in activity
print("PASS alpha22: executable SQLite v10→v11 preserves legacy, FK/cascade, all PCM and permission contracts")
