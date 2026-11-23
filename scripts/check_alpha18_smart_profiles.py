"""Alpha.18: SQLite v6->v7 preservation plus Android rule/user-action boundaries."""
from pathlib import Path
import sqlite3
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
history=(src/"LocalHistoryStore.kt").read_text(encoding="utf-8")
schema=(src/"SmartProfilesSchema.kt").read_text(encoding="utf-8")
repository=(src/"SmartProfilesRepository.kt").read_text(encoding="utf-8")
rules=(src/"SmartRules.kt").read_text(encoding="utf-8")
service=(src/"GameSessionRecorderService.kt").read_text(encoding="utf-8")
activity=(src/"SmartProfilesFragment.kt").read_text(encoding="utf-8")
compat=(src/"SmartProfilesActivity.kt").read_text(encoding="utf-8")
viewmodel=(src/"SmartProfilesViewModel.kt").read_text(encoding="utf-8")
manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
perms={x.get(ns+"name") for x in manifest.findall("uses-permission")}
expected={
    "android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE","android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS","android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY","android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
}
assert perms==expected,(perms,expected)
assert not ({"android.permission.QUERY_ALL_PACKAGES","android.permission.ACCESS_FINE_LOCATION",
            "android.permission.WRITE_SECURE_SETTINGS","android.permission.READ_PHONE_STATE"}&perms)
assert any(x.get(ns+"name")==".SmartProfilesActivity" and x.get(ns+"exported")=="false"
    for x in manifest.findall("./application/activity"))
qs=[x for x in manifest.findall("./application/service")
    if x.get(ns+"name")==".LegionQuickTileService"]
assert len(qs)==1 and qs[0].get(ns+"permission")=="android.permission.BIND_QUICK_SETTINGS_TILE"
assert qs[0].get(ns+"exported")=="true"
assert "startActivityAndCollapse(pending)" in (src/"LegionQuickTileService.kt").read_text(encoding="utf-8")
assert "SmartProfilesActivity::class.java" in (src/"MainActivity.kt").read_text(encoding="utf-8")
assert "const val VERSION = 7" in schema
assert 'versionCode = 43' in (root/"app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionName = "0.1.0-alpha.43"' in (root/"app/build.gradle.kts").read_text(encoding="utf-8")
assert 'oldVersion !in 1..11' in history
assert 'newVersion != HapticLabSchema.VERSION' in history
assert 'if (oldVersion <= 5) GameSessionSchema.install(db)' in history
assert history.count("SmartProfilesSchema.install(db)")==2
assert "DROP TABLE" not in history and "DELETE FROM samples" not in history

def extract(schema_source):
    tables=re.findall(r'db.execSQL\("""(.*?)""".trimIndent\(\)\)',schema_source,re.S)
    indexes=re.findall(r'db.execSQL\("(CREATE INDEX [^"]+)"\)',schema_source)
    assert len(tables)==3 and len(indexes)==3,(len(tables),len(indexes))
    return tables,indexes

legacy=re.findall(r'"""(CREATE TABLE (?:sessions|samples)\s*\(.*?\))"""',history,re.S)
assert len(legacy)==2
db=sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
for table in legacy: db.execute(table)
db.execute("CREATE INDEX idx_samples_session ON samples(session_id,recorded_at_ms)")
for file in ("DiagnosticHistoryModels.kt","SensorHistorySchema.kt",
             "SensorDiagnosticsSchema.kt","TimedBatterySchema.kt"):
    source=(src/file).read_text(encoding="utf-8")
    table=re.search(r'const val CREATE_TABLE\s*=\s*"""(.*?)"""',source,re.S).group(1)
    index=re.search(r'const val CREATE_INDEX\s*=\s*"([^"]+)"',source).group(1)
    db.execute(table);db.execute(index)
game=(src/"GameSessionSchema.kt").read_text(encoding="utf-8")
tables,indexes=extract(game)
for table in tables: db.execute(table)
for index in indexes: db.execute(index)
db.execute("INSERT INTO sessions(id,started_at_ms) VALUES('legacy',1000)")
db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,
 percent,temperature_deci_c,charging_status,charging_source)
 VALUES('legacy',2000,1000,55,370,'Discharging','None')""")
db.execute("""INSERT INTO game_sessions(session_id,package_name,app_label,started_at_ms,tcp_opt_in)
 VALUES('s1','com.example.game','Example',3000,0)""")
db.execute("""INSERT INTO game_samples(session_id,recorded_at_ms,battery_percent,network_type)
 VALUES('s1',4000,44,'WIFI')""")
db.execute("""INSERT INTO game_events(session_id,recorded_at_ms,event_type,details)
 VALUES('s1',4000,'TEST','old event')""")
db.execute("PRAGMA user_version=6")
historical={name:list(db.execute("SELECT * FROM "+name)) for name in
    ("sessions","samples","game_sessions","game_samples","game_events")}
tables,indexes=extract(schema)
with db:
    for table in tables: db.execute(table)
    for index in indexes: db.execute(index)
    db.execute("PRAGMA user_version=7")
assert db.execute("PRAGMA user_version").fetchone()[0]==7
for name,rows in historical.items():
    assert list(db.execute("SELECT * FROM "+name))==rows,name
db.execute("""INSERT INTO smart_profiles(package_name,label,created_at_ms,updated_at_ms,
 battery_hot_deci_c,battery_low_percent,tcp_opt_in)
 VALUES('com.example.game','Example',5000,5000,420,20,0)""")
db.execute("""INSERT INTO smart_rules(package_name,kind,threshold,cooldown_ms,enabled)
 VALUES('com.example.game','BATTERY_HOT',420,120000,1)""")
db.execute("""INSERT INTO smart_rule_events(session_id,package_name,kind,recorded_at_ms,details)
 VALUES('s1','com.example.game','BATTERY_HOT',6000,'Reached 42 C')""")
for bad in (
    "INSERT INTO smart_profiles VALUES('x','Short',1,1,420,20,0)",
    "INSERT INTO smart_profiles VALUES('com.other.game','',1,1,420,20,0)",
    "INSERT INTO smart_rules(package_name,kind,threshold,cooldown_ms,enabled) VALUES('com.none.game','BATTERY_HOT',420,120000,1)",
    "INSERT INTO smart_rules(package_name,kind,threshold,cooldown_ms,enabled) VALUES('com.example.game','BATTERY_HOT',420,120000,1)",
    "INSERT INTO smart_rules(package_name,kind,threshold,cooldown_ms,enabled) VALUES('com.example.game','UNSAFE_RULE',420,120000,1)",
    "INSERT INTO smart_rule_events(session_id,package_name,kind,recorded_at_ms,details) VALUES('missing','com.example.game','A',6000,'B')",
):
    try:db.execute(bad)
    except sqlite3.IntegrityError:continue
    raise AssertionError("Invalid SQL accepted: "+bad)
# deleting a profile must not cascade-delete historical game sessions or rule events
db.execute("DELETE FROM smart_profiles WHERE package_name='com.example.game'")
assert db.execute("SELECT COUNT(*) FROM smart_rules").fetchone()[0]==0
assert db.execute("SELECT COUNT(*) FROM game_sessions").fetchone()[0]==1
assert db.execute("SELECT COUNT(*) FROM smart_rule_events").fetchone()[0]==1
for name,rows in historical.items():
    assert list(db.execute("SELECT * FROM "+name))==rows,name
db.close()
for token in ("fun save(","fun delete(","fun setRule(","fun importBackup(",
              "db.beginTransaction()","db.setTransactionSuccessful()",
              "MAX_BACKUP","SmartRuleKind.entries","writeBackup(output"):
    assert token in repository,token
for token in ("fun evaluate(","lastFired","wasActive","networkValidated == false",
              "point.batteryTempDeciC","point.thermalStatus"):
    assert token in rules,token
for token in ("smartEngine.evaluate(point,smartRules)","smartStorage?.logAlert(",
              "checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)"):
    assert token in service,token
assert "GameSessionRecorderService.start" not in activity
assert "SmartProfilesFragment()" in compat
assert "state.observe(viewLifecycleOwner)" in activity
for token in ("SmartSystemControls.setBrightness", "SmartSystemControls.setDoNotDisturb",
              "ACTION_CREATE_DOCUMENT","ACTION_OPEN_DOCUMENT",
              "ViewModelProvider(this)[SmartProfilesViewModel::class.java]"):
    assert token in activity,token
for token in ("SmartSessionInsights.summarize", "SmartNetworkLab.inspect",
              "importBackup(it)", "SmartProfilesRepository(getApplication<Application>())",
              "onCleared()", "consumeDialog("):
    assert token in viewmodel,token
assert "android:exported=\"false\"" in (root/"app/src/main/AndroidManifest.xml").read_text()
print("PASS: real SQLite v6→v7 preservation/cascades/constraints and safe universal profile wiring")
