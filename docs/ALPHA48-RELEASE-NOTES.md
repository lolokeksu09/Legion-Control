# Legion Control v0.1.0-alpha.48

## Added
- Function Finder: 79 stable action identifiers across 21 modules; reveal the actual control without executing its action.
- Live Monitor: real Android telemetry, 2/5/10-second sampling, 120-sample limit, four charts, comparison point and explicit CSV export.
- Game Preflight: per-game diagnostic requirements, eleven readiness findings, a shareable report and guarded explicit launch.
- Session Compare: compare two records of one game across equal 5/10/15/30-minute windows; seven metrics, three common-scale graphs, missing-data gates, CSV and text reports.
- Session Library: filters, 20-row cursor pagination, selected session details and single-session diagnostic ZIP containing samples and events.

## Improved
- Direct access to Android settings and backup actions; two-line navigation labels and accessible tab state.
- Emulator regressions for feature navigation, foreground-only monitor, saved game requirements, local comparison and selected-session export.
- Corrected smoke-test return navigation to avoid repeated Back presses when the Home title is outside the viewport.

## Compatibility and limitations
- Package dev.lolokeksu.legioncontrol; SQLite user_version 12; existing permission allowlist and permanent signing certificate unchanged.
- No Root/Shizuku, synthetic game FPS or game-server ping, automatic global settings writes or background uploads.
- Live Monitor data is process-local; Game Preflight presets are outside the existing full-backup ZIP v1.
- Selected-session ZIP is diagnostic, not a restore archive. Export caps and missing samples are disclosed.
- Publication requires successful exact-main CI and permanent signing verification. Physical Lenovo Legion Y70, OEM-specific settings, MediaProjection/audio, Bluetooth and vibration tests remain separate and are not claimed by emulator tests.
