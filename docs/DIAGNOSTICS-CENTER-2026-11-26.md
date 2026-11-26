# Unified diagnostics center — 2026-11-26

Base: `9a8e4e5` (both Android CI and signed APK succeeded).

- DC-01: MainActivity is the single diagnostics host, with four selectable sections: manual Kotlin/Rust samples and battery test, Live Monitor, persisted history/export, sensors. Existing specialist tools are preserved but collapsed by default.
- DC-02: LiveMonitorPanel reuses the real LiveMonitorViewModel, chart, baseline and CSV export; the old LiveMonitorActivity is a compatibility redirect. No second sampler or service. Monitoring stops on leaving its section or the foreground; battery tests and sensor captures stop when their section closes.
- DC-03: Home and tool catalog have one diagnostics entry; History opens the history page; all stable diagnostic/monitor search IDs reveal their actual control without invoking it. Page and graph selection survive Activity recreation.
- DC-04: Stored session, thermal and battery-test reports and CSV/ZIP exports are grouped in History; saved battery-test results no longer overwrite active test progress. ViewModel retains the latest delivered text for each pane across configuration changes.
- Data and security: no schema, permissions, native ABI, release signer or new background work. Live monitor data remains in its bounded in-memory buffer until explicit CSV export; SQLite history stays separate and intact.

Verification: updated live-monitor source contract, new cross-module source guard and JVM routing tests. Android build and signed APK require exact-SHA CI. Device/emulator acceptance not performed in this session. Version remains alpha.59; no release tag is published automatically.

## CI follow-up

The first run on `6773312` failed in `check_shizuku_hub.py` because the former 19-module count did not reflect the merged Live Monitor. Three historical diagnostics guards also required exactly 25 buttons, although the new disclosure control makes 26. The catalog guard now checks the exact 18 unique module identities; the diagnostics guards check the relevant named actions, and the center guard checks all 25 retained actions plus the new disclosure. No safety or build gate is disabled. Local container has no GitHub network access; targeted source comparisons use the GitHub connector, and the full source suite/Android build must run in CI.
