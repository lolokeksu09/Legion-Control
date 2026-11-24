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

## Acceptance update (run 45363415073)

Candidate head 8f8919e344532088d993b9745d37d7b89786adc5: build and Android 35 emulator smoke completed/success. 55/55 source contracts (66 dialogs in 19 callers), 10 Rust tests, OSV 60 external components / zero affected versions, actual monitor readings and baseline lifecycle checks passed. The actual new confirmation opened; Cancel preserved the baseline and its report. Screenshot artifact 14906663830 contains 50 PNGs; reviewed contact sheets and full-resolution confirmation. The dialog uses the intended dark surface and hierarchy. Observed bottom tab label "Инструменты" splitting awkwardly across two lines; shortened only the shared visible shortTitle to "Инстр." (full accessibility title and routing retained). This change needs exact candidate CI and fresh visual evidence. No blanket all-dialog/font/physical-device visual PASS is claimed. Published version is still Alpha 48.
