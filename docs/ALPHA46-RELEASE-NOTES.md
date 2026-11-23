# Legion Control v0.1.0-alpha.46

This signed prerelease integrates the reorganized Legion Control interface from PR #51.
The published Alpha 45 package and its download link remain unchanged.

## Improved
- Games: Game Session, Game Toolbox, Smart Profiles, application library and favorites are grouped by user task.
- Tools: 11 existing modules in five searchable categories, with a one-tap reset for no matches and tool access even when the hub summary fails.
- History: game sessions, signals, incidents, baselines, recordings, experiments, device measurements and app history have clearer sections.
- Navigation: all five tabs have consistent names, and nested screens highlight their owning tab.
- Capture Studio: record/pause/resume/stop, Instant Replay and screenshots are prioritized ahead of encoding settings and game presets.
- Gaming, Hardware, Compatibility, Connectivity and Application Center modules group actions by purpose.
- Smart Profiles separates editing, analytics, network tests, device controls and backups.
- Legacy Kotlin/Rust diagnostics put manual measurements before other module shortcuts.
- Android emulator regression coverage now checks tool search and nested navigation.

## Preserved
- Application ID `dev.lolokeksu.legioncontrol`, permanent signing certificate, Android permissions, SQLite v12, Kotlin/Rust JNI and existing services.
- No Root, Shizuku, privileged OEM controls or simulated game FPS.

## Verification
Signed publication is gated on exact-main-SHA Kotlin/Rust CI, Android 15 emulator UI smoke and the pinned release-certificate check.
A real Lenovo Legion Y70 smoke test remains separate from the emulator and is not implied by successful CI.
