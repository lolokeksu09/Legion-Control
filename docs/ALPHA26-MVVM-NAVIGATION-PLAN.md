# Legion Control alpha.26 — MVVM Navigation + Game Hub (plan first)

**Baseline** main `45075c1fdafeed2cc12907cad52c417742df055e` alpha.25. Target `versionCode=26`, `0.1.0-alpha.26`; preserve package `dev.lolokeksu.legioncontrol`, existing permanent release signer, all Rust JNI, SQLite v12 and every existing screen.

## Decision: Navigation Component + MVVM with backend bridge
The user requested a complete nav architecture ("nvvml" = likely **MVVM**), with working backend preserved. Current `MainActivity.kt` is ~946 lines with working Kotlin/Rust diagnostics and history; moving/reimplementing that code would add unacceptable loss/regression risk. Keep `MainActivity` untouched as an **internal legacy Diagnostics screen** and give the launcher to `NavigationHostActivity` using **AndroidX Navigation Fragment 2.10.2** (official stable) and Lifecycle ViewModel/LiveData 2.11.1. New user-visible main screen is an AMOLED 5-tab hub. This is a migration *boundary*, not claiming all existing individual lab Activities have been rewritten to MVVM; that incremental refactor is future work.

- **View**: `NavigationHostActivity`, `HubPageFragment` (five explicit graph destinations), `GameDetailFragment`; Android view UI and back stack/navigation graph XML. Bottom tabs: Главная / Игры / Инструменты / История / Настройки; back returns correctly from game detail; selection restored across rotation through fragment arguments / ViewModel.
- **ViewModel**: `HubViewModel` (screen state, asynchronous refresh, user favorites toggle; no Android UI in repository). Reuses `LiveData`, lifecycle-aware observers; serialization of reads and writes to avoid races.
- **Repository**: `HubRepository` reads existing **`ApplicationCenterRepository`** (SQLite library and favorites), **`SmartProfilesRepository`** (saved profiles), **`CaptureClipLibrary`** (own clips), `PackageManager MAIN/LAUNCHER` (launcher-visible apps only); labels missing packages as unavailable, never assumes uninstalled. No new database migration or duplicates. Use existing `ToolboxPreferences` for explicit game selection. Navigation does not fabricate battery/FPS or bypass.
- **Backend bridge**: every existing lab retains its exact Activity/Kotlin/Rust implementation; expose all modules via grouped tools, Game Hub and history. The original `MainActivity` manual Rust + SQLite diagnostics remains available as an explicit route from history/tools. Foreground services remain independent and unmodified.
- **No Compose compiler, no new privileged permissions, no Root/Shizuku.** Use stable Views/Fragments Navigation to avoid throwing away the current Activity/Java UI and to minimize source changes.

## Requirements and validation
- **REQ-26-01** Launch new nav host, five stable destinations defined in XML, bottom navigation with selected state and proper back stack across rotations and app recreation; only one launcher entry; old MainActivity reachable internally.
- **REQ-26-02** Actual Game Hub loaded from Android's MAIN/LAUNCHER and saved SQLite game/app favorites; search, category game filter, choose detail, launch an actually visible application, add/remove favorite without discarding existing alias/category/note, and explicit selection for toolbox.
- **REQ-26-03** Home shows real stored counts, online/system capture availability without claiming active service or device health. Data fetching off UI thread; loading/empty/error states visible; return from old Activities refreshes state.
- **REQ-26-04** Tools/History/Settings reach all formerly reachable standalone modules (Game Toolbox, Capture Studio, Haptic, Audio, Application Center, Connectivity, Experiment, Smart Profiles, Game Session, Gaming/Hardware/Compatibility Labs, Gamepad, Display, legacy Diagnostics). No existing Rust JNI, SQLite, services or APIs removed.
- **REQ-26-05** Exact manifest permission allowlist unchanged; all services declaration order/types preserved. Original signer/package, SQLite schema v12 and Rust core byte-identical; no remote analytics, no background scanning. Existing permissions are opt-in.
- **REQ-26-06** Tests: test-first routes and favorites merge mapping with pure JVM contracts; static Navigation XML/manifest/backend wiring checks; run all old Python SQLite/contracts + JVM + Android compile/lint + Rust + release signer. Physical Lenovo/Motorola startup, back navigation, favorite DB, display layout and overlay/recording remain separate, cannot be marked passed by CI.

## Steps
1. Commit this plan before code. Build safe data/controller tests before implementation.
2. Add Navigation XML/host, MVVM repository/ViewModel and screens, all legacy module links and Game Hub.
3. Register new launcher; leave original MainActivity and prior lab code intact; bump version. Add strict navigation/backend bridge tests in both workflows and README.
4. PR, resolve CI failures, merge only passing exact candidate, verify main CI and permanent signed APK. Preserve hardware caveat.

## Risks
- Manifest launcher movement: must retain exactly one MAIN/LAUNCHER, keep original diagnostics explicitly non-exported and reachable via history/tools.
- Existing 150-item app library: favorite changes must not mutate unrelated fields or delete history. Reads/writes serialized on worker.
- Android package visibility restrictions: only query apps Android exposes via MAIN/LAUNCHER; unknown != uninstalled.
- Navigation graph state and background/rotation must not multiply SQLite operations or launch services.
- Existing old Activities remain manual; don't claim the entire codebase has migrated to MVVM before it has.

## Status
- [x] Inspected alpha.25 backend, manifest, all screen routes, core repos and old CI checks.
- [x] Chosen Architecture backed by current official stable AndroidX Navigation/Lifecycle.
- [x] Plan first commit.
- [ ] Code, tests and CI
- [ ] PR/release/sign
- [ ] Physical UI/device test
