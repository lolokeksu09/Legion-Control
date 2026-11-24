# Legion Control v0.1.0-alpha.48

## Added
- Function Finder: 79 stable action identifiers across 21 modules; reveal the actual control without executing its action.
- Live Monitor: real Android telemetry, 2/5/10-second sampling, 120-sample limit, four charts, comparison point and explicit CSV export.
- Game Preflight: per-game diagnostic requirements, eleven readiness findings, a shareable report and guarded explicit launch.
- Session Compare: compare two records of one game across equal 5/10/15/30-minute windows; seven metrics, three common-scale graphs, missing-data gates, CSV and text reports.
- Session Library: filters, 20-row cursor pagination, selected session details and single-session diagnostic ZIP containing samples and events.

## Improved
- Direct access to Android settings and backup actions; compact controls, readable navigation labels and accessible tab state.
- Emulator regressions for feature navigation, foreground-only monitor, saved game requirements, local comparison and selected-session export.
- Corrected smoke-test return navigation to avoid repeated Back presses when the Home title is outside the viewport.

## Fixed and hardened
- Stable view identities for all 79 finder actions; no text-based fallback or automatic action execution.
- Frozen selected export snapshots, lifecycle ownership, stale Preflight report expiry and safe navigation.
- Full backup confirmation bound to archive hashes; bounded journal reads and recoverable commit-marker replay.
- Streaming per-session CSV ZIP, exact truncation indicators, sample validity and percentage-point delta semantics.
- Periodic monitor reads only needed resource fields; no unmeasured speed or energy claims.
- Identical source gates in debug and signed workflows, pinned action SHAs/Rust, resolved runtime dependency inventories and release SBOM.

## Compatibility and limitations
- Package dev.lolokeksu.legioncontrol; SQLite user_version 12; existing permission allowlist and permanent signing certificate unchanged.
- No Root/Shizuku, synthetic game FPS or game-server ping, automatic global settings writes or background uploads.
- Live Monitor data is process-local. Full backup ZIP v2 includes Game Preflight presets; v1 archives remain readable and preserve existing Preflight presets. Older app versions cannot import ZIP v2.
- Selected-session ZIP is diagnostic, not a restore archive. Export caps and missing samples are disclosed.
- Publication requires successful exact-main CI and permanent signing verification. Physical Lenovo Legion Y70, OEM-specific settings, MediaProjection/audio, Bluetooth and vibration tests remain separate and are not claimed by emulator tests.
