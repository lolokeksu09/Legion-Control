# Game Preflight — per-game launch checklist

Base: Live Monitor / Function Finder development head `18af6f5b` (PR #56). This stacked change is separate from its CI. Merge the base before retargeting this patch to main. Published Alpha 47 is unchanged.

## User workflow

Open **Game Preflight** from Games, Tools, feature search, or a specific game detail. Choose an app exposed by Android's Launcher inventory (search by label/package). Set and save a separate checklist: target display refresh, minimum battery, maximum battery temperature, minimum available system RAM, validated network. The target is a user preference, not a write or FPS promise.

Check explicitly. Eleven findings cover thermal status, battery temperature, battery level, power saving, display refresh versus target and reported modes, available RAM, network validation, launcher availability, Capture Studio, Game Session, and unfinished global profile undo. Unknown values are not counted as normal. No performance score. No ping probe.

Open official Android settings or Game Toolbox / Game Session explicitly. Share the current report as plain text through Android's chooser. The checked-launch path rejects stale or changed snapshots, asks for confirmation if warnings or unknown values remain, and advises waiting if thermal limits are reported. Re-resolves Launcher Intent before launch; does not apply any system profile.

## Data and constraints

- Separate private SharedPreferences file `legion-preflight-v1`, up to 256 package-specific presets; validated versioned encoding; explicit commit; no changes to existing profile data or SQLite v12.
- These new presets are not in the existing full backup ZIP v1. Report is process-local and is not sent anywhere unless the user explicitly shares it. Existing ZIP import/export does not change the presets.
- No additional Android permissions, exported component, service, network request, root, Shizuku, GPU/CPU tuning or hidden APIs.
- Display mode is not in-game FPS; RAM is system-wide; temperatures are battery / Android thermal status, not CPU temperatures. Freshness window of 60 seconds is a UI guard, not a guarantee that the device's state stays unchanged.

## Verification

- JVM PreflightRulesTest checks per-game thresholds, unknown values, invalid OEM values, temperature limits, preset codec and freshness.
- Android instrumentation GamePreflightInstrumentedTest checks per-package persistent isolation, actual Launcher list, selecting/saving/checking real Android readings, invalidation after preset edits, no recorder or capture start.
- Emulator UI smoke opens the new screen from Games; static check_game_preflight.py enforces no global writes or service starts.
- Physical Lenovo testing and exact-head CI pending at commit time. No new signed release is published by this branch.
