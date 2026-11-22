"""Verify ten real user-facing backend modules, explicit permissions, no hidden privileges."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import sqlite3

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "GamingDiagnosticsActivity.kt").read_text(encoding="utf-8")
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
network = (src / "NetworkDiagnostics.kt").read_text(encoding="utf-8")
devices = (src / "DeviceInsights.kt").read_text(encoding="utf-8")
touch = (src / "FrameTouchDiagnostics.kt").read_text(encoding="utf-8")
usage = (src / "GameUsageDiagnostics.kt").read_text(encoding="utf-8")
sessions = (src / "SessionComparison.kt").read_text(encoding="utf-8")
store = (src / "LocalHistoryStore.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
perms = {e.attrib.get(android+"name") for e in manifest.findall("uses-permission")}
expected = {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS"
}
assert perms == expected, (perms, expected)
for forbidden in (
    "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_PHONE_STATE",
    "QUERY_ALL_PACKAGES", "WRITE_SECURE_SETTINGS", "SYSTEM_ALERT_WINDOW",
    "BIND_ACCESSIBILITY_SERVICE", "PACKAGE_INSTALL", "INTERNET" + "_ALWAYS"
):
    assert forbidden not in str(perms)
assert 'android.permission.PACKAGE_USAGE_STATS' in perms
assert "AppOpsManager.OPSTR_GET_USAGE_STATS" in usage
assert "Settings.ACTION_USAGE_ACCESS_SETTINGS" in activity
assert "hasUsageAccess(this)" in activity
assert "GameUsageDiagnostics.DEFAULT_PACKAGE" in activity

for n,label in enumerate((
    "Network Analyzer", "Battery Intelligence", "Thermal Guard",
    "Touch Analyzer", "Frame Pacing", "Wi-Fi / LTE Inspector",
    "Game Session Tracker", "Charging Analyzer", "Session Comparison",
    "System Health"
),1):
    assert f'"{n} · {label}' in activity, (n,label)

assert "NetworkDiagnostics.probe()" in activity
assert "TcpProbeStatistics.format(" in activity
assert "Socket()" in network and "TIMEOUT_MS = 1800" in network
assert 'NOT ICMP' in network and 'не ICMP' in network
assert "BATTERY_PROPERTY_CHARGE_COUNTER" in devices
assert "BATTERY_PROPERTY_ENERGY_COUNTER" in devices
assert "getThermalHeadroom(0)" in devices
assert "addThermalStatusListener" in activity and "removeThermalStatusListener" in activity
assert "TouchDiagnosticsView" in activity and "onTouchEvent(event: MotionEvent)" in touch
assert "Choreographer.FrameCallback" in touch and "5000L" in touch
assert "getNetworkCapabilities(active)" in devices
assert "UsageStatsManager" in usage and "queryEvents(from, now)" in usage
assert "ChargingDiagnostics.read(" in activity and "ChargeReading" in devices
assert "SessionAnalytics.analyze(" in activity and "sessionReadings(" in store
assert "ActivityManager.MemoryInfo()" in devices and "StatFs(" in devices
assert 'GamingDiagnosticsActivity::class.java' in main
assert "worker.shutdownNow()" in activity and "onStop()" in activity
assert "task?.cancel(true)" in activity
assert "frameCapture?.stop(false)" in activity
assert 'android:exported="false"' in (root / "app/src/main/AndroidManifest.xml").read_text()
assert "SQLiteOpenHelper" in store and "TimedBatterySchema.VERSION" in store
assert 'versionCode = 14' in gradle
assert 'versionName = "0.1.0-alpha.14"' in gradle

# The actual repo session query must be runnable with escaped bind values,
# not interpolated into a SQL string.
query = re.search(
    r'fun sessionReadings\(.*?"""(SELECT .*?)"""',
    store, re.S
)
assert query, "real session query missing"
db = sqlite3.connect(":memory:")
db.execute("""CREATE TABLE samples (
    id INTEGER PRIMARY KEY, session_id TEXT, recorded_at_ms INTEGER,
    elapsed_ms INTEGER,percent INTEGER,temperature_deci_c INTEGER,
    charging_status TEXT,charging_source TEXT,voltage_mv INTEGER,current_ua INTEGER,
    power_saving INTEGER,refresh_hz REAL,width_px INTEGER,height_px INTEGER
)""")
db.execute("""INSERT INTO samples(session_id,recorded_at_ms,elapsed_ms,percent,
 temperature_deci_c,charging_status,charging_source) VALUES
 ('a',2000,100,50,350,'Неизвестно','USB'),
 ('b',2100,110,60,360,'Неизвестно','USB'),
 ('a',2200,200,49,360,'Неизвестно','USB')""")
rows = db.execute(query.group(1), ("a", "250")).fetchall()
assert [row[3] for row in rows] == [49,50],rows
assert "WHERE session_id=?" in query.group(1), "must be SQL-parametrized"
db.close()
print("PASS: ten opt-in public Android modules, least-privilege manifest and real SQLite session comparisons")
