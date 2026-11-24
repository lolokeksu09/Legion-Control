# Legion Control v0.1.0-beta.1 — candidate

## Added
- Network Diagnostics 2.0: live TCP/DNS and gateway observations, IPv4/IPv6 selection, saved tests, A/B and CSV.
- Battery Lab 2.0: signed battery-terminal power, estimated energy integration with coverage, charts, archive, A/B and CSV.
- Game Automation: explicitly confirmed one-shot game launch profile, owner-bound undo, thermal and time guards, manual recovery.
- ZIP v3 includes both new lab archives, checksums and resumable recovery; reads ZIP v1/v2.

## Improved
- Compact AMOLED lab layouts, chart selector, progressive disclosure and accessible controls.
- Game Hub refresh coalescing, fewer SQLite/package lookups, search debounce and recorder event deduplication.
- CI process profiling: memory, CPU, screen observation and cold start, with exact artifact identity.

## Fixed
- Archive overwrite protection, interrupted recovery, CSV errors, late automation events, verified rollback, profile/ZIP restore interlock and CPU process identity parsing.

## Compatibility and limitations
- Package dev.lolokeksu.legioncontrol; versionCode 59; versionName 0.1.0-beta.1; SQLite v12 and separate lab archive schema v1; permanent signing fingerprint unchanged.
- No new Android permissions, root, Shizuku, fake FPS, game ping or charger-input power claims. Usage Access/overlay/notification and setting-specific Android access are explicit prerequisites.
- Only empty databases and (for v3) empty lab stores accept restore. No implicit deletion. Videos, screenshots, signing keys and transient automation report not included.
- Same-certificate update is intended; verify installed-data preservation. Do not uninstall the existing signed app.
- Emulator CI, measured profiling, visual and physical acceptance must be recorded for exact candidate before release. This file is a candidate description, not evidence of published APK or completed device tests.
