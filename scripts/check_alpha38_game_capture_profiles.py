#!/usr/bin/env python3
"""alpha38: per-game capture profile ownership, frozen consent, atomic JSON restore, no privilege."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
read = lambda f: (src / f).read_text(encoding="utf-8")
model = read("GameCaptureProfile.kt")
storage = read("GameCaptureProfiles.kt")
repo = read("CaptureStudioRepository.kt")
vm = read("CaptureStudioViewModel.kt")
ui = read("CaptureStudioActivity.kt")
service = read("CaptureRecordingService.kt")
unit = (root/"app/src/test/java/dev/lolokeksu/legioncontrol/GameCaptureProfileTest.kt").read_text(encoding="utf-8")
manifest = ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
permissions = {x.get(ns+"name") for x in manifest.findall("uses-permission")}
for forbidden in (
    "android.permission.READ_MEDIA_VIDEO", "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.WRITE_SECURE_SETTINGS", "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.BIND_ACCESSIBILITY_SERVICE"
):
    assert forbidden not in permissions, forbidden
assert "data class GameCaptureProfile(" in model
assert "CapturePresetRules.requireSupported(" in model
assert "data class ActiveGameCaptureSettings(" in model
assert "fun validPackage(" in model
assert "MAX_PROFILES = 64" in model
assert "MAX_JSON_BYTES = 128 * 1024" in model
assert unit.count("@Test") >= 8

# Exact validated persisted shape; never use arbitrary user file paths.
assert 'getSharedPreferences(\n        "legion-game-capture-profiles"' in storage
assert "prefs.edit().putString(target, encode(profile).toString()).commit()" in storage
assert "prefs.edit().remove(target).commit()" in storage
assert "prefs.edit().clear()" in storage
assert "editor.commit()" in storage
assert "val parsed = linkedMapOf<String, GameCaptureProfile>()" in storage
assert storage.index("for (i in 0 until array.length())") < storage.index("val editor = prefs.edit().clear()")
assert 'require(!parsed.containsKey(pkg))' in storage
assert 'require(root.keySet() == setOf("schema", "profiles"))' in storage
assert "require(array.length() <= GameCaptureProfilePolicy.MAX_PROFILES)" in storage
assert "GameCaptureProfilePolicy.validPackage(pkg)" in storage
assert 'val audio = obj.opt("audio") as? Boolean' in storage
assert 'val profile = runCatching { decode(JSONObject(value as String)) }.getOrNull()' in storage
assert 'fun backupJson()' in storage and 'fun restoreJson(text: String)' in storage
assert "fun delete(pkg: String): Boolean" in storage

# Selected game always from Game Toolbox and a package launchable under current visibility.
assert "getSharedPreferences(ToolboxPreferences.NAME" in repo
assert "getString(ToolboxPreferences.GAME, null)" in repo
assert "ApplicationInventory.isPackageName(it)" in repo
assert "getLaunchIntentForPackage(it) != null" in repo
assert "fun captureSettings(): ActiveGameCaptureSettings" in repo
assert "override ?: globalProfile()" in repo
assert "profiles.save(pkg, current.profile.setOption(name, value))" in repo
assert "profiles.save(pkg, current.profile.withPlaybackAudio(enabled))" in repo
assert "fun saveSelectedGameProfile()" in repo and "fun removeSelectedGameProfile()" in repo
assert 'CaptureConfig.fromOptions(' in repo
assert "app.contentResolver.openOutputStream(uri, \"wt\")" in repo
assert "app.contentResolver.openInputStream(uri)" in repo
assert "require(out.size() + len <= GameCaptureProfilePolicy.MAX_JSON_BYTES)" in repo

# Per-consent immutable capture snapshot: options/target/sound used only from a single instance.
assert "private var pendingCaptureConsent: ActiveGameCaptureSettings? = null" in vm
assert "fun rememberCaptureConsent(settings: ActiveGameCaptureSettings)" in vm
assert "fun takeCaptureConsent(): ActiveGameCaptureSettings?" in vm
assert "pendingCaptureConsent.also { pendingCaptureConsent = null }" in vm
assert "repository.importGameProfiles(uri)" in vm and "worker.execute {" in vm
assert "result.onSuccess" in vm
assert "viewModel.refreshSelectedGameSettings()" in ui
assert "viewModel.rememberCaptureConsent(settings)" in ui
assert "viewModel.clearCaptureConsent()" in ui
assert "val snapshot = viewModel.takeCaptureConsent()" in ui
assert "val config = snapshot.profile.toConfig()" in ui
assert "val playbackRequested = snapshot.profile.playbackAudio" in ui
assert "val audioGame = if (playbackRequested) snapshot.selectedGame else null" in ui
assert ui.index("val snapshot = viewModel.takeCaptureConsent()") < ui.index("startForegroundService(intent)")
assert "Intent.ACTION_CREATE_DOCUMENT" in ui and "Intent.ACTION_OPEN_DOCUMENT" in ui
assert "viewModel.exportGameProfiles(data.data!!)" in ui
assert "viewModel.importGameProfiles(uri)" in ui
assert "setPositiveButton(\"Импортировать\")" in ui

assert "getMediaProjection(Activity.RESULT_OK, consent)" in service
assert "CaptureConfig.fromOptions(" in service
assert "EXTRA_PLAYBACK_AUDIO" in service and "EXTRA_AUDIO_GAME" in service
assert "onStartCommand(intent: Intent?" in service
for dangerous in ("Runtime.getRuntime", "Shizuku.", "ProcessBuilder(", "MediaProjectionManager", "AudioRecord(", "android.permission."):
    assert dangerous not in storage + model, dangerous
print("PASS alpha38: strict per-game local capture presets, consent-frozen settings, atomic SAF backup/restore, no new access")
