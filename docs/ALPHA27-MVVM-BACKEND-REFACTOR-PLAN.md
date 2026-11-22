# Legion Control alpha.27 — MVVM backend refactor, stage 2 (plan first)

Baseline `main` `e6387b4be19043fa9e6cb042af2ce5fa6ebce300` (alpha.26). User authorized continuing **full app refactor**, with priority on backend rather than visual redesign. Version 27 / `0.1.0-alpha.27`, same application ID, permanent certificate, Rust JNI, SQLite v12, services and permission allowlist. No Root/Shizuku.

## Audited architecture and work stages
- `NavigationHostActivity`, `HubPageFragment`, `HubViewModel`, `HubRepository`: new navigation with MVVM boundary already in alpha.26.
- `MainActivity.kt` (946 LOC): Activity currently owns Rust sampling, SQLite persistence and read-only history formatting, executor, sensor callbacks, timed test lifecycle, export and UI.
- `CaptureStudioActivity.kt` (306 LOC): Activity currently owns preferences, async MediaStore library/delete executor, system MediaProjection consent and UI.
- `GameSessionActivity`, `SmartProfilesActivity`, `AudioLabActivity`, `HapticLabActivity`, `ApplicationCenterActivity`, `ConnectivityLabActivity`, `ExperimentLabActivity`, `GamingDiagnosticsActivity`, `HardwareLabActivity` etc. remain Activity-oriented.

**This PR is a bounded, backend-first, testable stage of the full migration**, not false claim that all twenty+ legacy Activities are fully migrated.

## Requirements (alpha.27)
- R27-01: `DiagnosticsHistoryRepository` becomes the single reader/formatter for existing SQLite history, thermal, recent sensor outcomes, sensor quality, session profiles and bounded timed-test reports. Do not change schema, joins, units, null handling, CSV, native APIs or save order.
- R27-02: `DiagnosticsHistoryViewModel` uses a single worker, lifecycle LiveData, per-field result/error/loading state, and serialized reads. Legacy `MainActivity` observes it, and no longer constructs read-only SQL reports inline or runs those read-only queries on the UI thread. Manual Rust sampling, sensor/timed capture lifecycle and existing writing/export remain unchanged this patch to avoid invalidating native/persistence ordering.
- R27-03: `CaptureStudioRepository` owns persisted user quality presets and safe **app-owned** MediaStore list/delete; `CaptureStudioViewModel` handles one executor and lifecycle-aware clip list and deletion results. Activity only renders, shows delete confirmation and calls the existing one-time MediaProjection intent/foreground service. No cached screen token or altered service FGS behavior.
- R27-04: `HubViewModel` refresh/action concurrency avoids race and stale loading; no UI retained callbacks or background main-thread database call. Search/selection/back stack remains as in alpha.26.
- R27-05: Refactor tests protect unchanged history formatting, per-action request generations, media list/delete only via repository and UI hooks, unchanged permissions/DB; new strict static contract in CI and pure JVM behavioral tests.
- R27-06: All historical static/SQLite/Kotlin/Rust/lint/signed release tests pass. Distinguish Android-build vs physical Y70 Motorola-ROM smoke tests; physical acceptance remains external.

## Execution
1. Create isolated branch and commit this plan FIRST; add pure tests before refactor.
2. Implement diagnostic read repository + VM and adapt `MainActivity` read-only panels, leaving write/record/export path untouched.
3. Implement CaptureStudio Repository + VM; move executor and preferences from Activity; keep consent/stop behavior unchanged.
4. Fix HubViewModel's refresh/write scheduling; add tests.
5. Bump v27, revise exact-version checks, add contract to both CI workflows; docs & PR.
6. Merge only if exact SHA CI success; verify permanently signed main APK and provide direct download. Hardware verification separate.

## Explicit deferred stages (needed for *complete* architecture migration)
- Migrate active recording/sensor/timed-test coordination to lifecycle-aware controllers with lifecycle tests before moving the 250+ line `MainActivity.sample()`; JNI must remain native.
- Migrate `GameSessionActivity`, Smart Profiles, Application Center, Audio/Haptic/Connectivity/Experiment labs individually, preserving existing DB contracts and service lifecycles.
- Convert remaining Activity UI to fragments as appropriate, unify events/error/loading/navigation, remove old redundant launcher buttons only after all invocations are accessible.
These are **not complete** merely because stage 2 compiles.

## Safety/risk
- Any existing SQL reads on shared `LocalHistoryStore` must be closed off-main, writer should not be closed while queued writes remain. Activity lifecycle close remains on original write executor; new read helper owns its own database helper and closes it during worker teardown.
- Capturing MediaProjection requires fresh Android consent every time; don't move Activity result token into ViewModel.
- Fragment/activity recreation should not cause duplicate deletion or replay a destructive callback; user confirmation is required for every delete.
- If a test depends on exact old file location, update it to check **actual moved backend implementation**, not weaken assertions.
