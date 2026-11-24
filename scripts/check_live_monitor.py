#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
model = (src / "LiveMonitorModels.kt").read_text()
vm = (src / "LiveMonitorViewModel.kt").read_text()
ui = (src / "LiveMonitorActivity.kt").read_text()
chart = (src / "MonitorChartView.kt").read_text()
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml")
a = "{http://schemas.android.com/apk/res/android}"
activity = manifest.find("./application/activity[@"+a+"name='.LiveMonitorActivity']")
assert activity is not None and activity.get(a+"exported") == "false"
assert all("LiveMonitor" not in x.get(a+"name", "") for x in manifest.findall("./application/service"))
assert "MAX_POINTS = 120" in model
assert "GameSessionTelemetry.snapshot(app)" in vm and "ResourceTelemetryBackend.readForMonitor(app)" in vm
assert "override fun onStop()" in ui and "model.setVisible(false)" in ui
assert "token != generation" in vm and "main.removeCallbacks(tick)" in vm
assert "ActivityResultContracts.CreateDocument" in ui and "openOutputStream(uri" in vm
assert "pendingDocument.begin(s.points.toList())" in vm and "model.beginExport()" in ui
assert "pendingDocument.take()" in vm and "model.finishExport(uri)" in ui
assert "m.deltaUnit" in ui
resource = (src / "ResourceTelemetryBackend.kt").read_text()
assert "fun readForMonitor(" in resource
assert "previousX = null" in chart and "point.elapsedMs" in chart
assert "CsvEscaper.cell" in model
for banned in ("startForegroundService(", "startService(", "WorkManager", "WakeLock", "Settings.System.put", "Runtime.getRuntime", "ProcessBuilder", "HttpURLConnection"):
    assert banned not in (model + vm + ui + chart), banned
print("PASS: foreground-only real telemetry, bounded samples, private Activity and explicit SAF CSV")
