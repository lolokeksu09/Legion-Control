# Legion Control v0.1.0-alpha.49

## Added
- Unified in-app AMOLED dialog presentation: shared rounded panel, accessible titles, native confirmation, choice and input semantics across 66 call sites in 19 screens.
- Shared compact chart-frame engine with elapsed-time axis, actual units, readable extrema and explicit no-data state for Live Monitor, Game Session and Session Compare.
- Common semantic loading, error, warning and empty-data surfaces; consistent report cards across nested diagnostic modules.

## Improved
- Compact Game Detail actions and Hub spacing; abbreviated visible Tools navigation label ("Инстр.") while retaining the full accessible "Инструменты" title and route.
- Consistent styling for native dropdown controls in Audio Lab, Haptic Lab and Game Preflight.
- High-severity Preflight findings use shared danger/warning/success colors; Capture Studio library states have explicit empty/loading/error visual treatment.

## Tests
- Shared dialog semantics, cancel without applying changes, item selection, multiple-choice persistence, input and shared charts checked in Android 35 emulator instrumentation.
- Android navigation and real local session smoke, saved Live Monitor baseline after cancellation and chart/report states checked with exact-revision CI.
- Existing SQLite, Kotlin and Rust regressions, runtime SBOM/OSV scan and permanent APK signature gates retained.

## Compatibility / limitations
- Package: dev.lolokeksu.legioncontrol; versionCode 49, versionName 0.1.0-alpha.49.
- SQLite schema v12, permissions, JNI ABIs and pinned permanent signing certificate unchanged. No Root, Shizuku, fake FPS or privileged controls.
- Android system permission prompts, MediaProjection consent, document pickers and shares remain Android-owned.
- UI changes are additive; current user history and preferences are preserved by in-place same-certificate upgrade. Do not uninstall the previous release.
- Successful emulator and CI do not establish behavior on a physical Lenovo Legion Y70. Physical device acceptance remains a separate L5 gate.
