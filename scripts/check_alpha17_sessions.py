"""Alpha.17: real SQLite v5->v6 migration and universal, opt-in recording contracts."""
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
history=(src/"LocalHistoryStore.kt").read_text(encoding="utf-8")
schema=(src/"GameSessionSchema.kt").read_text(encoding="utf-8")
repository=(src/"GameSessionRepository.kt").read_text(encoding="utf-8")
service=(src/"GameSessionRecorderService.kt").read_text(encoding="utf-8")
activity=(src/"GameSessionActivity.kt").read_text(encoding="utf-8")
fragment=(src/"GameSessionFragment.kt").read_text(encoding="utf-8")
viewmodel=(src/"GameSessionViewModel.kt").read_text(encoding="utf-8")
capture=(src/"GameSessionTelemetry.kt").read_text(encoding="utf-8")
analysis=(src/"GameSessionAnalysis.kt").read_text(encoding="utf-8")
manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
perms={x.attrib.get(ns+"name") for x in manifest.findall("uses-permission")}
expected={
 "android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
 "android.permission.ACCESS_WIFI_STATE","android.permission.PACKAGE_USAGE_STATS",
 "android.permission.FOREGROUND_SERVICE",
 "android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
 "android.permission.POST_NOTIFICATIONS",
    "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
}
assert perms == expected,perms
assert not ({"android.permission.QUERY_ALL_PACKAGES","android.permission.ACCESS_FINE_LOCATION",
 "android.permission.READ_PHONE_STATE","android.permission.WRITE_SECURE_SETTINGS"} & perms)
fgs=manifest.find("./application/service")
assert fgs is not None
assert fgs.attrib[ns+"name"]==".GameSessionRecorderService"
assert fgs.attrib[ns+"exported"]=="false"
assert fgs.attrib[ns+"foregroundServiceType"]=="specialUse"
assert fgs.find("property") is not None
assert manifest.find("queries/intent") is not None
assert any(x.attrib.get(ns+"name")==".GameSessionActivity" for x in manifest.findall("./application/activity"))
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionCode = 41' in gradle and 'versionName = "0.1.0-alpha.41"' in gradle
assert 'minSdk = 33' in gradle
assert "HapticLabSchema.VERSION" in history
assert "oldVersion !in 1..11" in history
assert "if (oldVersion <= 4) installTimedBatteryTests(db)" in history
assert history.count("GameSessionSchema.install(db)") == 2
assert "DROP TABLE" not in history and "DELETE FROM samples" not in history
assert "const val VERSION = 6" in schema

# Replay a real v5 database and prove no history is lost on additive v6 DDL.
legacy=re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""',history,re.S)
assert len(legacy)==2
def original_table(name):
    source=(src/name).read_text(encoding="utf-8")
    match=re.search(r'const val CREATE_TABLE\s*=\s*"""(.*?)"""',source,re.S)
    assert match,name
    idx=re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"',source)
    assert idx,name
    return match.group(1),idx.group(1)

db=sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
for sql in legacy: db.execute(sql)
db.execute("CREATE INDEX idx_samples_session ON samples(session_id,recorded_at_ms)")
for file in ("DiagnosticHistoryModels.kt","SensorHistorySchema.kt","SensorDiagnosticsSchema.kt","TimedBatterySchema.kt"):
    table,index=original_table(file)
    db.execute(table)
    db.execute(index)
db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('legacy',1000)")
db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,
 percent,temperature_deci_c,charging_status,charging_source)
 VALUES('legacy',2000,1000,51,365,'Discharging','None')""")
db.execute("PRAGMA user_version=5")
before=list(db.execute("SELECT * FROM samples"))
assert before

v6ddl=re.findall(r'db.execSQL\("""(.*?)""".trimIndent\(\)\)',schema,re.S)
v6indexes=re.findall(r'db.execSQL\("(CREATE INDEX [^"]+)"\)',schema)
assert len(v6ddl)==3,len(v6ddl)
assert len(v6indexes)==3,len(v6indexes)
with db:
    for sql in v6ddl: db.execute(sql)
    for sql in v6indexes: db.execute(sql)
    db.execute("PRAGMA user_version=6")
assert list(db.execute("SELECT * FROM samples")) == before
assert db.execute("PRAGMA user_version").fetchone()[0]==6
db.execute("""INSERT INTO game_sessions
 (session_id,package_name,app_label,started_at_ms,tcp_opt_in)
 VALUES('run1','com.example.game','Example Game',3000,0)""")
db.execute("""INSERT INTO game_samples
 (session_id,recorded_at_ms,battery_percent,battery_temp_deci_c,thermal_status,
 power_saving,network_type,network_validated)
 VALUES('run1',3010,50,370,3,1,'WIFI',0)""")
db.execute("""INSERT INTO game_events(session_id,recorded_at_ms,event_type,details)
 VALUES('run1',3020,'NETWORK','validation changed')""")
assert db.execute("SELECT COUNT(*) FROM game_samples").fetchone()[0]==1
assert db.execute("SELECT COUNT(*) FROM game_events").fetchone()[0]==1
for bad_sql in (
 "INSERT INTO game_samples(session_id,recorded_at_ms,network_type) VALUES('invalid',3010,'WIFI')",
 "INSERT INTO game_samples(session_id,recorded_at_ms,battery_percent,network_type) VALUES('run1',3010,200,'WIFI')",
 "INSERT INTO game_events(session_id,recorded_at_ms,event_type,details) VALUES('invalid',3010,'A','B')",
):
    try: db.execute(bad_sql)
    except sqlite3.IntegrityError: continue
    raise AssertionError("Invalid session SQL accepted")
assert list(db.execute("SELECT * FROM samples")) == before
db.close()

for term in ("startForegroundService(i)","ACTION_STOP","START_NOT_STICKY",
 "startForeground(NOTICE,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)",
 "stopForeground(STOP_FOREGROUND_REMOVE)","registerDefaultNetworkCallback",
 "unregisterNetworkCallback","addThermalStatusListener","removeThermalStatusListener",
 "scheduleWithFixedDelay","GameSessionRepository","record(id,point)",
 "tcpAllowed &&","NetworkDiagnostics.probe()","stopSelf()"):
    assert term in service,term
for term in ("BatteryManager.EXTRA_TEMPERATURE","BATTERY_PROPERTY_CURRENT_NOW",
 "currentThermalStatus","isPowerSaveMode","NET_CAPABILITY_VALIDATED"):
    assert term in capture,term
for term in ("ACTION_MAIN","CATEGORY_LAUNCHER","startRecording()","stopRecording()",
 "GameSessionRecorderService.start","GameSessionRecorderService.stop",
 "Intent.ACTION_CREATE_DOCUMENT","ViewModelProvider(this)[GameSessionViewModel::class.java]",
 "GameSessionRecorderService.start","GameSessionRecorderService.stop"):
    assert term in fragment,term
for term in ("GameSessionAnalysis.render","GameSessionAnalysis.compare",
             "exportZip","writeCsv(writer", "GameSessionRepository(getApplication())",
             "onCleared()", "LiveData", "latest.get() == request"):
    assert term in viewmodel,term
for term in ("fun writeCsv(","fun exportZip(","fun recent(","fun points(","fun event(",
 "dbHelper.writableDatabase","UUID.randomUUID","if(c.isNull"):
    assert term in repository,term
assert "warnings(s)" in analysis
assert "GameSessionActivity::class.java" in (src/"MainActivity.kt").read_text(encoding="utf-8")
assert "GameSessionFragment()" in activity and "state.observe(viewLifecycleOwner)" in fragment
# The same package-name validator must handle both typed packages and navigation
# extras; a separately escaped regex silently discards Smart Profiles selection.
assert "packagePattern.matches(pkg)" in fragment
assert "packagePattern.matches(it)" in fragment
assert fragment.count('Regex("^[A-Za-z]') == 1, "one canonical package regex"
assert 'arguments?.getString("smart_package")?.takeIf {' in fragment
for forbidden in ("ACCESS_FINE_LOCATION","QUERY_ALL_PACKAGES","WRITE_SECURE_SETTINGS",
 "Runtime.getRuntime().exec","setprop ","ShizukuService"):
    assert forbidden not in service+capture+activity+fragment+repository
print("PASS: real v5→v6 preservation, FK/constraints and universal user-started service/export wiring")
