#!/usr/bin/env python3
"""One diagnostics host, preserved deep links and foreground-only measurement ownership."""
from pathlib import Path
import re
src = Path(__file__).resolve().parents[1] / "app/src/main/java/dev/lolokeksu/legioncontrol"
read = lambda file: (src / file).read_text()
host = read("MainActivity.kt")
monitor = read("LiveMonitorActivity.kt")
hub = read("HubPageFragment.kt")
catalog = read("HubToolCatalog.kt")
history = read("DiagnosticsHistoryViewModel.kt")
page = read("DiagnosticsPage.kt")
assert "pagePanels.getValue(DiagnosticsPage.MONITOR).addView(monitorPanel.root)" in host
assert "LegionUiSupport.spinner(this, visiblePages.map { it.label })" in host
assert "DiagnosticsPage.entries.filter { it.isPeripheral == peripheral }" in host
assert "monitorPanel.setVisible(foreground && next == DiagnosticsPage.MONITOR)" in host
assert "monitorPanel.setVisible(false)" in host[host.index("override fun onStop()"):]
assert "timedTest?.stop(TimedBatteryStopReason.APP_BACKGROUND)" in host[host.index("private fun selectPage"):host.index("override fun onStart()")]
assert "if (currentPage == DiagnosticsPage.SENSORS) cancelSensorCapture()" in host
assert "startActivity(Intent(this, MainActivity::class.java)" in monitor
assert "finish()" in monitor[:monitor.index("internal class LiveMonitorPanel")]
assert "model.state.observe(activity, ::render)" in monitor
assert "FeatureReveal.fromActivity(activity, root, FeatureModule.MONITOR)" in monitor
assert "FeatureModule.MONITOR -> openStandaloneFeature(MainActivity::class.java, args)" in hub
assert "putString(MainActivity.EXTRA_PAGE, DiagnosticsPage.HISTORY.name)" in hub
assert "LiveMonitorActivity::class.java" not in catalog
assert "MainActivity::class.java" in catalog
assert '"timed" -> timedHistoryView.text = reading.content' in host
assert "cachedReports[kind.pane] = result" in history and "historyModel.latestReports().forEach(::renderHistory)" in host
assert "parse(saved)?.let { return it }" in page
assert '"diag.thermal", "diag.zip" -> HISTORY' in page
assert "FeatureFinder.resolve(featureId, FeatureModule.MONITOR)" in page
for action in (
    "manualSample()", "startTimedBatteryTest()", "stopTimedBatteryTest()",
    "showTimedBatteryHistory()", "shareReport()", "exportCsv()", "confirmReset()",
    "showSessionProfile()", "showSensorQuality()", "exportDiagnosticArchive()",
    "showSavedHistory()", "showThermalHistory()", "checkSensors()",
    "startSensorCapture()", "showLastSensorReadings()",
    "openPage(DiagnosticsPage.NETWORK)", "openPage(DiagnosticsPage.GAMING)",
):
    assert re.search(r"setOnClickListener\s*\{(?:(?!\n\s*\}).)*" + re.escape(action), host, re.S), action
assert "host?.openPage(page)" in read("ModuleNavigation.kt")
assert "mountLab(next)" in host and "transaction.commitNow()" in host
assert host.index("if (currentPage == DiagnosticsPage.MONITOR) monitorPanel.setVisible(false)") < host.index("mountLab(next)")
assert "putString(MainActivity.EXTRA_PAGE,diagnosticPage.name)" in hub
assert "transaction.remove(old)" in host and "transaction.replace(R.id.device_lab_container,fragment,tag)" in host
for name in ("GamingDiagnostics","HardwareLab","ConnectivityLab","CompatibilityLab","AudioLab","HapticLab"):
    assert name+"Fragment()" in host
    code=read(name+"Fragment.kt")
    assert 'arguments?.getBoolean("embedded_center")==true' in code and "override fun onStop()" in code
print("PASS: one device/peripheral host, embedded labs, stable navigation, and bounded foreground-only telemetry")
