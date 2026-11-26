#!/usr/bin/env python3
"""Guard alpha.33 manual per-game actions; do not turn this into passive tracking."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
policy = (src / "GameLaunchProfilePolicy.kt").read_text(encoding="utf-8")
engine = (src / "GameLaunchProfiles.kt").read_text(encoding="utf-8")
ui = (src / "GameToolboxFragment.kt").read_text(encoding="utf-8")
actions = (src / "GameLaunchProfileActions.kt").read_text(encoding="utf-8")
overlay = (src / "GameToolboxService.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/GameLaunchProfilePolicyTest.kt").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
actual = {x.get(ns + "name") for x in manifest.findall("uses-permission")}
required = {"android.permission.WRITE_SETTINGS", "android.permission.ACCESS_NOTIFICATION_POLICY",
            "android.permission.MODIFY_AUDIO_SETTINGS"}
assert required.issubset(actual)
assert "android.permission.QUERY_ALL_PACKAGES" not in actual
assert "android.permission.WRITE_SECURE_SETTINGS" not in actual
assert "android.permission.BIND_ACCESSIBILITY_SERVICE" not in actual
assert "GameLaunchProfileActions.confirmApply(" in ui
assert "GameLaunchProfiles.applyConfirmed(context, pkg, profile)" in actions
assert "GameLaunchProfiles.launchConfirmed(context, pkg, profile)" in actions
assert "GameLaunchProfiles.restoreConfirmed(context, pending)" in actions
launch = engine[engine.index("internal fun launchConfirmed"):engine.index("internal fun applyAutomatic")]
assert launch.index("ProfileMaintenanceGate.tryRun") < launch.index("applyConfirmed") < launch.index("openGame()") < launch.index("restore(context)")
assert "pendingConfirmation(context) != approved" in engine
assert ".putString(GENERATION, UUID.randomUUID().toString())" in engine
assert ".remove(OWNER).remove(GENERATION).commit()" in engine
assert "(owner != null || approved != null) && p != approved" in engine
assert engine.index("p != approved") < engine.index("val snapshot = runCatching")
assert "GameLaunchProfiles.apply(this, pkg)" in overlay and "GameLaunchProfiles.restore(this)" in overlay
editor = (src / "GameLaunchProfileEditor.kt").read_text(encoding="utf-8")
assert "GameLaunchProfiles.save(ctx, pkg, profile)" in editor
assert "GameLaunchProfileEditor.show(ctx, pkg)" in ui
assert "setView(scroll)" in editor and "confirmApplyGameLaunchProfile" in ui
assert "confirmRestoreGameLaunchProfile" in ui
assert "ApplicationInventory.launchIntent(context, pkg)" in actions
assert 'edit().putString(ACTIVE, snapshot.encode())' in engine
assert engine.index('journal.commit()') < engine.index('audio.setStreamVolume(')
assert 'journal.putString(OWNER,owner)' in engine and 'restoreAutomatic' in engine
assert "Settings.System.canWrite(context)" in engine
assert "nm.isNotificationPolicyAccessGranted" in engine
assert "GameLaunchProfilePolicy.restoreChoice(read(), change)" in engine
assert "CHANGED_BY_USER" in engine
assert "GameLaunchUndo.decode(raw)" in engine
assert "Build.VERSION.SDK_INT >= 35" in engine
assert "getAutomaticZenRuleState" in engine
assert "rule.owner == null && rule.configurationActivity == null" in engine
assert "ticket.focusScope == FocusUndoScope.GLOBAL_FILTER" in engine
assert "legacyFocusSkipped" in engine
assert "FocusUndoScope.APP_RULE" in policy
assert "AppDndRuleSnapshot" in policy
assert "rule.interruptionFilter" in engine
assert "AutomaticZenRule.Builder(rule)" in engine
assert "updateAutomaticZenRule" in engine
assert "current.encoded != requested.encoded" in engine
assert tests.count("@Test") >= 10
# Every launch-profile mutation and ZIP replay follows one lock order:
# maintenance gate first, then the profile ticket monitor. In particular,
# automatic rollback must not acquire the monitor before waiting for the gate.
auto_restore = engine[engine.index("internal fun restoreAutomatic"):engine.index("private fun applyInternal")]
manual_restore = engine[engine.index("fun restore(context"):engine.index("private fun restoreLocked")]
assert auto_restore.index("ProfileMaintenanceGate.tryRun") < auto_restore.index("synchronized(lock)")
assert manual_restore.index("ProfileMaintenanceGate.tryRun") < manual_restore.index("synchronized(lock)")
assert "private fun restoreLocked(context: Context)" in engine
restore_locked = engine[engine.index("private fun restoreLocked"):engine.rindex("\n}")]
assert "return@synchronized" not in restore_locked
assert "return if (!saved)" in restore_locked
backup = (root / "app/src/main/java/dev/lolokeksu/legioncontrol/LegionDataBackup.kt").read_text(encoding="utf-8")
export = backup[backup.index("@Synchronized fun export"):backup.index("fun preview")]
assert "ProfileMaintenanceGate.tryRun" in export
assert export.index("ProfileMaintenanceGate.tryRun") < export.index("checkArchiveWritesAllowed")
assert "pending_global_undo" in export
assert 'key == "pending_undo_generation"' in backup
assert 'key != "pending_undo_generation"' in backup




assert "ACTION_MANAGE_WRITE_SETTINGS" not in engine
assert all(forbidden not in engine for forbidden in (
    "queryUsageStats", "queryEvents(", "AccessibilityService", "startForegroundService",
    "startActivity(", "Shell", "Runtime.getRuntime", "java.net", "java.io.File"
))
assert tests.count("@Test") >= 8
assert "SmartProfilesRepository" not in engine
assert "LocalHistoryStore" not in engine
assert "android.permission" not in policy
print("PASS alpha33: user-initiated global actions, preflight, durable restore, no new privileges or SQLite migration")
