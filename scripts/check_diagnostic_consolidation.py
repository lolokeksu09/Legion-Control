"""Guard shared readers, explicit manual overview and exclusive lab captures."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
read = lambda name: (src / (name + ".kt")).read_text(encoding="utf-8")
devices = read("DeviceInsights")
resources = read("ResourceTelemetryBackend")
gaming = read("GamingDiagnosticsViewModel")
hardware = read("HardwareLabViewModel")
assert "getThermalHeadroom(0)" not in devices
assert "ResourceTelemetryBackend.readThermalHeadroom(context)" in devices
assert "ResourceTelemetryBackend.readBatteryCounters(context)" in devices
assert "ResourceTelemetryBackend.readSystemMemory(context)" in devices
assert "ResourceTelemetryBackend.readAppFilesystem(context)" in devices
assert "val counters = readBatteryCounters(context)" in resources
assert "GamingProbe.OVERVIEW -> DeviceInsights.overview" in gaming
assert "task?.isDone==false || frameCapture != null || thermalListener != null" in gaming
assert "runningJob?.isDone==false || motion != null || networkEvents != null" in hardware
assert "runningJob?.isDone == false || networkEvents != null" in hardware
assert "runningJob?.isDone == false || motion != null" in hardware
assert "generation.get() == token" in gaming
assert "AudioRouteInspector.readOutputs(context)" in read("GamingCompatibilityBackend")
assert "AudioRouteInspector.label(out.type)" in read("GamingCompatibilityBackend")
for lab in ("GamingDiagnosticsFragment", "HardwareLabFragment", "CompatibilityLabFragment"):
    body = read(lab)
    assert "DiagnosticLabUi.action(" in body
    assert "DiagnosticLabUi.revealResult(" in body
    assert "Остановить текущую проверку" in body
assert "LegionUiSupport.actionTile" in read("DiagnosticLabUi")
print("PASS: shared telemetry, one manual overview, exclusive lab capture and common accessible controls")
