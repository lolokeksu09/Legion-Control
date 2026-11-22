#!/usr/bin/env python3
"""Stage 3: protect real MVVM boundaries, foreground consent, native writer and legacy routes."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
read=lambda name: (src/name).read_text(encoding="utf-8")
main=read("MainActivity.kt")
rust=read("RustSampleController.kt")
game=read("GameSessionFragment.kt")
gameActivity=read("GameSessionActivity.kt")
gamevm=read("GameSessionViewModel.kt")
profiles=read("SmartProfilesFragment.kt")
profilesCompat=read("SmartProfilesActivity.kt")
profilevm=read("SmartProfilesViewModel.kt")
apps=read("ApplicationCenterFragment.kt")
appsCompat=read("ApplicationCenterActivity.kt")
appvm=read("ApplicationCenterViewModel.kt")
audio=read("AudioLabFragment.kt")
audioCompat=read("AudioLabActivity.kt")
audiovm=read("AudioLabViewModel.kt")
haptic=read("HapticLabActivity.kt")
hapticvm=read("HapticLabViewModel.kt")
graph=(root/"app/src/main/res/navigation/legion_nav_graph.xml").read_text(encoding="utf-8")
manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
permissions={item.get(ns+"name") for item in manifest.findall("uses-permission")}
assert "android.permission.WRITE_SECURE_SETTINGS" not in permissions
assert "android.permission.QUERY_ALL_PACKAGES" not in permissions
assert "android.permission.RECORD_AUDIO" not in permissions
assert "android.permission.READ_MEDIA_VIDEO" not in permissions
assert "private external fun recordBatterySample(" in read("RustEngine.kt")
assert "sampleController.sample()" in main and "sampleController.resetSession()" in main
assert "engine.record(" not in main
for token in ("engine.record(percent, temp, elapsedMs)","engine.statistics()",
              "engine.durationMs()", "writer.execute {", "history.record(StoredReading(",
              "history.recordDiagnostic(", "acceptedSessionId == null || !primarySave.second",
              "mainHandler.post {", "!cleared && requestId == diagnosticRequestId",
              "writer.execute { runCatching { history.close() } }",
              "engine.resetSession()", "onCleared()"):
    assert token in rust, token
assert rust.index("history.record(StoredReading(") < rust.index("history.recordDiagnostic(")
assert "registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))" in rust
assert "runOnUiThread" not in rust and "Activity" not in rust.split("class RustSampleController(",1)[1]

for vm in (gamevm, profilevm, appvm, audiovm, hapticvm):
    for token in ("AndroidViewModel", "LiveData", "Executors.newSingleThreadExecutor()",
                  "onCleared()"):
        assert token in vm, token
    assert "runOnUiThread" not in vm
    assert "AlertDialog" not in vm
    assert "startActivity(" not in vm

assert "GameSessionRecorderService.start(requireContext()," in game
assert "GameSessionFragment()" in gameActivity
assert "GameSessionRecorderService.stop(requireContext())" in game
assert "Intent.ACTION_CREATE_DOCUMENT" in game
assert "GameSessionRepository(getApplication())" in gamevm
assert "GameSessionAnalysis.compare(" in gamevm
assert "worker.execute {" in gamevm
assert "latest.get() == request" in gamevm
assert "GameSessionRepository(" not in game
assert "SmartProfilesRepository(" not in profiles
assert "SmartProfilesRepository(getApplication<Application>())" in profilevm
assert "SmartSessionInsights.summarize(" in profilevm
assert "repo.importBackup(it)" in profilevm
assert "setPositiveButton(\"Импортировать\")" in profiles
assert "consumeDialog(nonce: Int)" in profilevm
assert "ApplicationCenterRepository(" not in apps
assert "ApplicationCenterRepository(getApplication<Application>())" in appvm
assert "visible.get() || closed.get()" in appvm
assert "viewModel.setVisible(false)" in apps
assert "setPositiveButton(\"Удалить\")" in apps
assert "AudioRouteMonitor(application)" in audiovm
assert "backend.play(type,which,hz,length)" in audiovm
assert "backend.stop()" in audiovm
assert "viewModel.stopPlayback()" in audio and "viewModel.stopRouteMonitor()" in audio
assert "AudioLabRepository(" not in audio
assert "backend.perform(waveform)" in hapticvm and "backend.stop()" in hapticvm
assert "HapticLabRepository(" not in haptic
assert "viewModel.stopPlayback()" in haptic
assert "setPositiveButton(\"Удалить\")" in haptic
assert "ViewModelProvider(this)" in game and "state.observe(viewLifecycleOwner)" in game
assert "ViewModelProvider(this)" in apps and "state.observe(viewLifecycleOwner)" in apps
assert "ApplicationCenterFragment()" in appsCompat
assert "ViewModelProvider(this)" in profiles and "state.observe(viewLifecycleOwner)" in profiles
assert "SmartProfilesFragment()" in profilesCompat
assert "ViewModelProvider(this)" in audio and "state.observe(viewLifecycleOwner)" in audio
assert "AudioLabFragment()" in audioCompat
assert "ViewModelProvider(this)" in haptic and "state.observe(this)" in haptic

# Stage 3 does NOT falsely claim a completed all-Fragment migration.
assert "homeFragment" in graph and "gameDetailFragment" in graph
assert "NavigationHostActivity" in read("NavigationHostActivity.kt")
assert "gameSessionFragment" in graph and "GameSessionFragment" in game
assert "navigate(R.id.gameSessionFragment,extras)" in read("HubPageFragment.kt")
print("PASS alpha28: explicit native recorder + five real lifecycle backends, permissions, cancellation, consent")
