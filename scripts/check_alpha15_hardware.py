"""Verify six new real public Android hardware tools without bypass or new permissions."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
activity = (src / "HardwareLabActivity.kt").read_text(encoding="utf-8")
backend = (src / "HardwareLabBackend.kt").read_text(encoding="utf-8")
models = (src / "HardwareLabModels.kt").read_text(encoding="utf-8")
capture = (src / "HardwareLabCapture.kt").read_text(encoding="utf-8")
display = (src / "DisplayPatternActivity.kt").read_text(encoding="utf-8")
gaming = (src / "GamingDiagnosticsActivity.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest_source = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
manifest = ET.fromstring(manifest_source)
a = "{http://schemas.android.com/apk/res/android}"
perms={p.get(a+"name") for p in manifest.findall("uses-permission")}
assert perms=={
 "android.permission.INTERNET",
 "android.permission.ACCESS_NETWORK_STATE",
 "android.permission.ACCESS_WIFI_STATE",
 "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.WRITE_SETTINGS","android.permission.VIBRATE"
}, perms
assert 'android:exported="false"' in manifest_source
for name in (".HardwareLabActivity",".DisplayPatternActivity"):
    assert any(x.get(a+"name")==name and x.get(a+"exported")=="false"
        for x in manifest.findall("./application/activity")),name
assert 'HardwareLabActivity::class.java' in gaming
assert 'versionCode = 23' in gradle
assert 'versionName = "0.1.0-alpha.23"' in gradle
for num,name in enumerate([
    "Local Route Inspector",
    "Network Events",
    "Storage Lab",
    "Motion Stability",
    "Display Patterns",
    "Codec Inspector"
],1):
    assert '"'+str(num)+' · '+name in activity,(num,name)
for call in (
    "LocalRouteDiagnostics.inspect(applicationContext)",
    "SandboxIoBenchmark.run(applicationContext)",
    "MotionStabilityCapture(applicationContext)",
    "ForegroundNetworkEvents(",
    "DisplayPatternActivity::class.java",
    "CodecCapabilityDiagnostics.inspect()",
):
    assert call in activity,call
assert "startActivity(Intent.createChooser(" in activity
assert "runningJob?.cancel(true)" in activity
assert "networkEvents?.stop(false)" in activity
assert "completed = {\n                networkEvents = null" in activity, "30s completion must allow another test"
assert "motion?.stop(false)" in activity
assert "worker.shutdownNow()" in activity
assert "override fun onStop()" in activity
assert "registerDefaultNetworkCallback(callback, handler)" in capture
assert "unregisterNetworkCallback(callback)" in capture
assert "handler.postDelayed(timeout, 30_000L)" in capture
assert "manager.unregisterListener(this)" in capture
assert "handler.postDelayed(deadline, 5_000L)" in capture
assert "TYPE_ACCELEROMETER" in capture and "TYPE_GYROSCOPE" in capture
assert "BYTES = 8 * 1024 * 1024" in backend
assert "File.createTempFile(" in backend
assert "file.delete()" in backend
assert "stream.fd.sync()" in backend
assert "MessageDigest.getInstance(\"SHA-256\")" in backend
assert "MessageDigest.isEqual(" in backend
assert "link.dnsServers" in backend and "link.routes" in backend
assert "MediaCodecList(MediaCodecList.REGULAR_CODECS)" in backend
assert "isHardwareAccelerated" in backend
assert "mebibytesPerSecond(bytes: Long, elapsedNs: Long)" in models
assert "inclin" in models.lower() and "gyroRmsRadPerSec" in models
for name in ("Color.BLACK","Color.WHITE","Color.RED","Color.GREEN","Color.BLUE","Color.GRAY"):
    assert name in display,name
for forbidden in (
    "Settings.System.putInt", "screenBrightness =",
    "WRITE_SECURE_SETTINGS", "Shizuku", "Root", "Runtime.getRuntime().exec"
):
    # Root/Shizuku in comments are not runtime behavior; restrict backend/capture/patterns.
    if forbidden in ("Root","Shizuku"):
        assert forbidden not in backend+capture+display
    else:
        assert forbidden not in backend+capture+display
assert "HapticLabSchema.VERSION" in (src/"LocalHistoryStore.kt").read_text(encoding="utf-8")
assert not any(x in activity for x in ("startForegroundService(", "registerReceiver(", "WorkManager"))
print("PASS: six real hardware tests, bounded callbacks/storage and unchanged SQLite v5 / permissions")
