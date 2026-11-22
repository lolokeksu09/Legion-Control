# Legion Control — Game Launch Profiles (next backend patch)

## Functional scope

A manual, persistent **per-game launch profile** in the existing Game Toolbox. No new diagnostics or passive sampling. Choose any launcher-visible application, then configure up to three independent global Android actions:

- **Media volume**: don't change, 0/20/40/60/80/100% of device stream maximum.
- **Brightness**: don't change, 20/40/60/80/100% using `Settings.System`; applying a brightness target also selects manual brightness mode.
- **Do Not Disturb**: don't change, enable (NONE), disable (ALL). The previous Android interruption filter is stored for undo, including PRIORITY and ALARMS.

Actions are applied **only after an explicit user tap**, either separately or before launching the selected game. The floating toolbox has direct **Profile / Restore profile** actions. Profiles are private SharedPreferences records keyed by the actual selected package name; removing all actions deletes the package's profile. Invalid packages, corrupted configurations, or unsupported DND state are rejected rather than guessed.

## Safety and undo contract

1. Check *all* relevant system permissions and read all original values before changing any. WRITE_SETTINGS and Notification Policy access are requested manually through existing Android Settings buttons. Failure means no attempted writes.
2. Commit one per-device global undo ticket **before** the first system write. An outstanding ticket prevents a new application from overwriting the old original values.
3. If the application crashes, the undo ticket survives process death; restoring is explicitly user-initiated, never a background process or boot receiver.
4. Undo separately compares every current setting with what this profile requested. Already-original values are safe; settings changed by the user or another app after applying are **not** overwritten. Unsuccessful writes stay pending for a manual retry. If the user modified brightness, auto-brightness mode is not blindly restored.
5. Android API success only means a request was accepted; it does **not** guarantee a given firmware applied the setting. Profiles do not control CPU/GPU, FPS, refresh rate, Bypass Charging, another app's audio policy, or manufacturer game services.

The existing manual Game Toolbox DND/brightness/volume buttons remain independent. The older `SmartSystemControls.restoreScreenSettings()` store is untouched. An independent global profile undo may not perfectly recreate user/system changes made concurrently between Android reads and writes; compare-before-restore minimizes clobbering but cannot prove causal ownership of a numerical value.

## Compatibility

- `dev.lolokeksu.legioncontrol` remains unchanged; no Root, Shizuku, Accessibility, QUERY_ALL_PACKAGES or new Android permissions.
- SQLite v12 and existing game sessions, Rust/JNI ABI and original signing configuration are untouched.
- No network operations, game background launch detector, auto-apply or foreground telemetry added.
- `versionCode` remains 27 in this source-only functional increment because existing historic contract scripts pin it; a separate validated release task must coordinate that bump across guards and artifact names.

## Acceptance & physical device checks

- Select two different packages; set different values; persist across Activity recreation; choose one and apply.
- With all permissions, apply and manually restore media, brightness, auto/manual mode and existing PRIORITY/ALARMS DND state; verify actual Android Settings before and after.
- Remove WRITE_SETTINGS or Notification Policy access and confirm preflight rejects the whole operation without partial changes.
- Change volume/brightness/DND manually after applying; restore must not overwrite the user-changed value. A partially failed restoration remains pending.
- Reboot/force-stop while an undo ticket exists; manually restore after restarting the Activity. An already-pending ticket must block another game.
- Android 13–16: check explicit apply, apply+launch, overlay profile buttons, cancellation of setup, missing/unlaunchable app, rotation, and permission denial.
- Build: `gradle --no-daemon testDebugUnitTest`, `bash scripts/build-native.sh`, `gradle --no-daemon assembleDebug lintDebug` plus all existing repository guards. Emulator and physical Lenovo Y70 remain separately reportable.

New tests: `GameLaunchProfilePolicyTest` covers codec, opt-in settings, package validation, conversion, corruption rejection and compare-before-restore. Android global-setting behavior needs physical-device validation; no mock or static inspection is claimed as proof.
