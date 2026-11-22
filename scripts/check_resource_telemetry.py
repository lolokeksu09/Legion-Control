"""Read-only resource telemetry contracts; functional JVM tests cover values and failures."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
models = (src / "ResourceTelemetryModels.kt").read_text(encoding="utf-8")
backend = (src / "ResourceTelemetryBackend.kt").read_text(encoding="utf-8")
gate = (src / "ThermalHeadroomGate.kt").read_text(encoding="utf-8")
combined = (src / "DeviceDiagnosticsBackend.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/ResourceTelemetryTest.kt").read_text(encoding="utf-8")
rate_tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/ThermalHeadroomGateTest.kt").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()

assert set(x.attrib["{http://schemas.android.com/apk/res/android}name"] for x in manifest.findall("uses-permission")) == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.WRITE_SETTINGS","android.permission.VIBRATE","android.permission.SYSTEM_ALERT_WINDOW"
}, "Public telemetry must not require permissions"
for value in (
    "ProbeState", "AVAILABLE, UNAVAILABLE, ERROR, RATE_LIMITED",
    "fun <T : Any> read(", "fun memory(", "fun storage(",
    "fun currentMicroAmps(", "fun chargeCounterMicroAh(",
    "fun energyCounterNanoWh(", "fun thermalHeadroom(",
    "data class ResourceTelemetrySnapshot(",
):
    assert value in models, value

for value in (
    "ActivityManager.MemoryInfo()", "getMemoryInfo(memory)",
    "StatFs(context.filesDir.absolutePath)", "availableProcessors()",
    "BATTERY_PROPERTY_CHARGE_COUNTER", "BATTERY_PROPERTY_CURRENT_NOW",
    "BATTERY_PROPERTY_CURRENT_AVERAGE", "BATTERY_PROPERTY_ENERGY_COUNTER",
    "getIntProperty(propertyId)", "getLongProperty(", "getThermalHeadroom(0)",
    "SystemClock.elapsedRealtime()", "thermalGate.sample(nowElapsedMs)",
):
    assert value in backend, value

assert "minimumIntervalMs: Long = 1500L" in gate
assert "minimumIntervalMs >= 1000L" in gate
assert "@Synchronized" in gate and "lastAttemptElapsedMs = nowElapsedMs" in gate
assert "DeviceCapabilitiesBackend.read(context)" in combined
assert "ResourceTelemetryBackend.read(context)" in combined

for marker in (
    "fun probeResultsDistinguishReadingsFromFailures()",
    "fun readGuardDistinguishesExceptionsAndInvalidManufacturerValues()",
    "fun memoryValidationRejectsInconsistentKernelReports()",
    "fun batteryPropertiesKeepZeroAndSignedCurrentButRejectSentinels()",
    "fun frequentInvocationsSkipThePlatformWithoutReturningStaleSamples()",
    "fun rebootOrClockResetReopensTheMonotonicSamplingWindow()",
):
    assert marker in tests + rate_tests, marker

for forbidden in (
    "WRITE_SECURE_SETTINGS", "QUERY_ALL_PACKAGES", "Runtime.getRuntime().exec",
    "setprop ", "moto_extend_service", "Shizuku", "WRITE_EXTERNAL_STORAGE",
):
    # Shizuku can appear in explanatory comments, but not executable references.
    if forbidden != "Shizuku":
        assert forbidden not in backend + models + gate + combined, forbidden
assert "startService(" not in backend + gate + combined
assert "registerListener(" not in backend + gate + combined

print("PASS: public Android resource probes, failures, thermal rate gate, no new permissions")
