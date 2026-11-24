# Alpha 49 development — unified AMOLED presentation

Base published Alpha 48: 40ec6e46ba658e0e3ac8956e703286242a9c286b. Development branch feat/alpha49-unified-dialogs. Version metadata remains Alpha 48 for this review-only candidate because the published release and existing version gates must not be overwritten. Increment version and release metadata in a separate authorized release preparation change.

## Implemented

- Shared LegionDialogs: 66 call sites migrated across 19 interactive screens; common compact theme, title, rounded panel, native confirmation/list/single/multi selection/input, explicit danger actions. Android AlertDialog remains the underlying lifecycle/accessibility implementation. This is app-owned presentation, not a replacement for OS authorization/SAF/MediaProjection/chooser. Fullscreen display and touch test remain special-purpose.
- Shared selectors in Audio Lab, Haptic Lab and Game Preflight; same native selection and filtering behavior.
- LegionChartFrame unifies actual elapsed-time axes, unit labels, plot geometry, line/point styles and no-data presentation in MonitorChartView, SessionCompareChart and GameSessionTimelineView. Existing data sources and Compare 45-second gap guard preserved. Flat ranges plotted at center; timeline accessible description exposes actual valid sample count and extrema. No generated measurements or smoothing added.
- Shared semantic notice and report surfaces, applied to 12 nested-module reports plus session/compare/library/monitor/Preflight and Capture Studio; explicit typed loading/error/empty states in Hub, Game Detail and Capture Studio. No generic message-text classification. Some existing ViewModels still expose string-only status; not all error classifications have been converted.
- Compact Game Detail action cards and Hub spacing; Preflight severity palette centralized. No icon, package, permission, SQLite or signing changes.

## Verification
- Source inventory: 66 migrated builders in 19 files during first patch; new static contract scans main Kotlin sources for bypasses and checks actual chart/report call sites. Full gate not executed locally.
- Added instrumentation for confirmation/cancellation, choice semantics, custom text input, shared plot geometry, flat/invalid ranges, status appearance and selector state. Existing monitor suite expected exactly 5 tests. Real confirmation dialog screenshot + cancel scenario added to Android smoke harness. Full CI, screenshot review, narrow/font-scaled landscape/TalkBack and physical device checks still pending.
- Published Alpha 48 remains unchanged; no release or merge until exact candidate checks.
