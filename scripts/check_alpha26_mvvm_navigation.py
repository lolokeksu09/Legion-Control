#!/usr/bin/env python3
"""Alpha.26: genuine five-destination MVVM Navigation shell, backend bridge and no data loss."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root/"app/src/main"
src = app/"java/dev/lolokeksu/legioncontrol"
manifest = ET.parse(app/"AndroidManifest.xml").getroot()
graph = ET.parse(app/"res/navigation/legion_nav_graph.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
res = "{http://schemas.android.com/apk/res-auto}"
gradle = (root/"app/build.gradle.kts").read_text()
activity = (src/"NavigationHostActivity.kt").read_text()
view = (src/"HubPageFragment.kt").read_text()
detail = (src/"GameDetailFragment.kt").read_text()
vm = (src/"HubViewModel.kt").read_text()
repo = (src/"HubRepository.kt").read_text()
rules = (src/"HubNavigationRules.kt").read_text()
tests = (root/"app/src/test/java/dev/lolokeksu/legioncontrol/HubNavigationRulesTest.kt").read_text()
legacy = (src/"MainActivity.kt").read_text()
sample_controller = (src/"RustSampleController.kt").read_text()
store = (src/"LocalHistoryStore.kt").read_text()
rust = (root/"rust-core/src/lib.rs").read_text()

assert 'versionCode = 27' in gradle and 'versionName = "0.1.0-alpha.27"' in gradle
assert 'applicationId = "dev.lolokeksu.legioncontrol"' in gradle
assert 'androidx.navigation:navigation-fragment:2.10.2' in gradle
assert 'androidx.lifecycle:lifecycle-viewmodel:2.11.1' in gradle
assert 'androidx.lifecycle:lifecycle-livedata:2.11.1' in gradle
assert "HapticLabSchema.VERSION" in store and "HISTORY_LIMIT: usize = 64" in rust
assert "engine.record(percent, temp, elapsedMs)" in sample_controller
assert "engine.statistics()" in sample_controller
assert "sampleController.sample()" in legacy
assert "GameSessionActivity::class.java" in legacy

permissions = {x.get(android+"name") for x in manifest.findall("uses-permission")}
assert permissions == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS", "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.ACCESS_NOTIFICATION_POLICY", "android.permission.WRITE_SETTINGS",
    "android.permission.VIBRATE", "android.permission.SYSTEM_ALERT_WINDOW",
}, permissions
assert not manifest.findall("./application/receiver")
services = manifest.findall("./application/service")
assert services[0].get(android+"name") == ".GameSessionRecorderService"
assert {s.get(android+"name") for s in services} == {
    ".GameSessionRecorderService", ".GameToolboxService",
    ".CaptureRecordingService", ".LegionQuickTileService",
}

activities = {a.get(android+"name"): a
    for a in manifest.findall("./application/activity")}
assert ".MainActivity" in activities and activities[".MainActivity"].get(android+"exported")=="false"
assert activities[".NavigationHostActivity"].get(android+"exported")=="true"
launcher = []
for activity_entry in manifest.findall("./application/activity"):
    for f in activity_entry.findall("intent-filter"):
        actions = {a.get(android+"name") for a in f.findall("action")}
        categories = {c.get(android+"name") for c in f.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            launcher.append(activity_entry.get(android+"name"))
assert launcher == [".NavigationHostActivity"], launcher
for old in (
    ".MainActivity",".GameToolboxActivity",".CaptureStudioActivity",
    ".GameSessionActivity",".SmartProfilesActivity",".ApplicationCenterActivity",
    ".GamingDiagnosticsActivity",".HardwareLabActivity",".CompatibilityLabActivity",
    ".ExperimentLabActivity",".ConnectivityLabActivity",".AudioLabActivity",
    ".HapticLabActivity",".GamepadLiveActivity",".DisplayPatternActivity"
):
    assert old in activities, old

assert graph.tag == "navigation"
assert graph.get(res+"startDestination") == "@id/homeFragment"
nodes = graph.findall("fragment")
assert len(nodes) == 18
assert {n.get(android+"id") for n in nodes} == {
    "@+id/homeFragment", "@+id/gamesFragment", "@+id/toolsFragment",
    "@+id/historyFragment", "@+id/settingsFragment", "@+id/gameDetailFragment", "@+id/gameSessionFragment", "@+id/sessionSignalsFragment", "@+id/sessionBaselinesFragment",
    "@+id/applicationCenterDestination", "@+id/smartProfilesDestination",
    "@+id/audioLabDestination", "@+id/hapticLabDestination",
    "@+id/connectivityLabDestination", "@+id/experimentLabDestination",
    "@+id/compatibilityLabDestination", "@+id/hardwareLabDestination",
    "@+id/gamingDiagnosticsDestination"
}
for node in nodes:
    assert node.get(android+"name") in {
        "dev.lolokeksu.legioncontrol.HubPageFragment",
        "dev.lolokeksu.legioncontrol.GameDetailFragment",
        "dev.lolokeksu.legioncontrol.GameSessionFragment",
        "dev.lolokeksu.legioncontrol.SessionSignalsFragment",
        "dev.lolokeksu.legioncontrol.SessionBaselinesFragment",
        "dev.lolokeksu.legioncontrol.ApplicationCenterFragment",
        "dev.lolokeksu.legioncontrol.SmartProfilesFragment",
        "dev.lolokeksu.legioncontrol.AudioLabFragment",
        "dev.lolokeksu.legioncontrol.HapticLabFragment",
        "dev.lolokeksu.legioncontrol.ConnectivityLabFragment",
        "dev.lolokeksu.legioncontrol.ExperimentLabFragment",
        "dev.lolokeksu.legioncontrol.CompatibilityLabFragment",
        "dev.lolokeksu.legioncontrol.HardwareLabFragment",
        "dev.lolokeksu.legioncontrol.GamingDiagnosticsFragment"
    }
    if node.get(android+"id") == "@+id/gameSessionFragment":
        assert [x.get(android+"name") for x in node.findall("argument")] == [
            "smart_package", "smart_label", "smart_tcp"]
    elif node.get(android+"id") in ("@+id/audioLabDestination","@+id/hapticLabDestination",
                                  "@+id/connectivityLabDestination","@+id/compatibilityLabDestination",
                                  "@+id/hardwareLabDestination", "@+id/gamingDiagnosticsDestination"):
        assert len(node.findall("argument")) == 0
    else:
        assert len(node.findall("argument")) == 1
detail_node = [n for n in nodes if n.get(android+"id") == "@+id/gameDetailFragment"][0]
assert detail_node.find("argument").get(android+"name") == "gamePackage"

assert "FragmentActivity" in activity and "NavHostFragment.create(" in activity
assert "NavigationHostActivity" in activity and "HubViewModel::class.java" in activity
assert "setRestoreState(true)" in activity and "setLaunchSingleTop(true)" in activity
assert "setPopUpTo(R.id.homeFragment, false, true)" in activity
assert "onDestinationChangedListener" in activity or "addOnDestinationChangedListener" in activity
assert "R.id.gameDetailFragment" in activity and "nav.navigateUp()" in activity
assert "onResume()" in activity and "refresh()" in activity

assert "ViewModelProvider(requireActivity())[HubViewModel::class.java]" in view
assert "state.observe(viewLifecycleOwner)" in view
assert "HubTab.parse(arguments?.getString(" in view
assert "HubSearch.matches(" in view
assert "R.id.gameDetailFragment" in view and "gamePackage" in view
assert "ApplicationCenterActivity::class.java" in view
assert "MainActivity::class.java" in view and "CaptureStudioActivity::class.java" in view
assert "GameSessionActivity::class.java" in view and "GameToolboxActivity::class.java" in view
assert "HapticLabActivity::class.java" in view and "AudioLabActivity::class.java" in view
assert "setOnClickListener" in view and "onResume()" in view

assert "toggleFavorite(pkg, app.label)" in detail
assert "R.id.gameSessionFragment" in detail
assert 'putString("smart_package",pkg)' in detail
assert 'putExtra("app_center_package", pkg)' in detail
assert "getLaunchIntentForPackage(pkg)" in detail

assert "AndroidViewModel" in vm and "LiveData" in vm
assert "Executors.newSingleThreadExecutor()" in vm
assert "backend.read()" in vm and "backend.toggleFavorite(" in vm
assert "worker.execute" in vm and "onCleared()" in vm
assert "ApplicationCenterRepository(app).use" in repo
assert "SmartProfilesRepository(app).use" in repo
assert "GameSessionRepository(app).use" in repo
assert "CaptureClipLibrary.listOwn(app)" in repo
assert "ApplicationInventory.launchable(app)" in repo
assert "HubFavoriteRules.toggle(" in repo
assert "repository.save(pkg, fields.alias, fields.category, fields.note, fields.favorite)" in repo
assert "ToolboxPreferences.GAME" in repo
assert "PackageManager.ApplicationInfoFlags.of(0L)" in repo
assert "CATEGORY_GAME" in repo
assert "READ_MEDIA_VIDEO" not in repo
assert "fun toggle(" in rules and "current.copy(favorite = !current.favorite)" in rules
assert "HubNavigationRulesTest" in tests and tests.count("@Test") >= 5

assert "QUERY_ALL_PACKAGES" not in str(permissions)
assert "WRITE_SECURE_SETTINGS" not in str(permissions)
assert "RECORD_AUDIO" not in str(permissions)
assert "AccessibilityService" not in activity + repo + vm + view + detail
assert "Shizuku" not in repo + vm
print("PASS alpha26: only new launcher, 5 real NavHost tabs+detail, MVVM, original Rust/SQLite and permission isolation")
