# Legion Control — alpha.28 MVVM continuation (stage 3)

Base: merged alpha.27 on main `63018d35cf04839dc795fda07a29d2c64ab61bb7`. This is a staged, non-destructive refactor, not authorization to change the signed release or the installed app.

## Contract
- R28-01: Extract the real Android battery/display snapshot -> Rust JNI validation -> SQLite primary/child persistence and report flow from `MainActivity.sample()` into an Activity-independent controller. Keep manual and opt-in timed sampling triggers, JNI implementation, cached Rust window, native reset, session boundaries, row ordering and report text.
- R28-02: No database migration, network/privileged access, Root/Shizuku, permission or signer change; reuse a single existing write queue so sample rows precede diagnostic rows, timed summaries and read projections.
- R28-03: Make the Activity observe controller results without allowing an obsolete queued diagnostic result to repaint the screen after native reset, destroy or a later sample. No Activity, Views or lifecycle owner retained by the backend.
- R28-04: Then migrate Game Session, Smart Profiles, Application Center and remaining laboratory read/action coordinators behind lifecycle-safe ViewModels, one bounded screen at a time. Keep explicit consent/permission UI and long-lived foreground services at their Android ownership boundaries.
- R28-05: Unify screens as Navigation Component destinations only once the relevant state/events are isolated. Preserve existing deep links, selected package arguments, back handling and all current tool routes; no deleting old Activity paths prematurely.
- R28-06: Gate changed Kotlin via Gradle assembleDebug/lintDebug/JVM tests and existing Rust/SQLite/security/signing checks. Physical Motorola/Lenovo emulator/device behavior remains NOT RUN unless actually observed.

## Order
1. Extract Rust sample controller, add narrow contract test and static wiring regression; verify source and CI.
2. Migrate Game Session / Smart Profiles / Application Center backends to screen ViewModels; preserve state across rotations and avoid unsolicited writes.
3. Apply lifecycle ViewModel pattern to Audio/Haptic/Connectivity/Experiment and compatible labs, with explicit boundaries for permissions, sensors and system UI.
4. Migrate safe UI destinations into Navigation Component incrementally. Remove duplicated Activity UI only after routes and back stack are proven.
5. Release only after required gates and device smoke tests, without changing permanent release identity.

## Risks and rollback
- Rust native buffer is process-global; do not accidentally instantiate independent recorders or auto-reset across configuration changes.
- Shared SQLite helper is closed only behind queued writes; never close while accepted sample jobs are outstanding.
- Recorded events and permission prompts must not replay after rotation; no ViewModel should own Activity references or MediaProjection tokens.
- Any failure can be rolled back by reverting this isolated feature branch; main stays alpha.27 until review.

## Implementation checkpoint — 2026-11-22

This branch contains code for, but does not yet release:
- **DONE (source):** real Rust manual/timed sampling, JNI/window stats, battery/report and serialized SQLite primary+diagnostic writes -> `RustSampleController` (AndroidViewModel).
- **DONE (source):** Game Session history, temperature timeline, comparison and user-selected CSV/ZIP write -> `GameSessionViewModel`; recorder foreground service remains Android service.
- **DONE (source):** Smart Profiles CRUD, rule selection/persistence, insights, opt-in network probe and confirmed JSON import/export -> `SmartProfilesViewModel`.
- **DONE (source):** Application Center library, audit/usage, manual scan cancel-on-leave and CSV -> `ApplicationCenterViewModel`.
- **DONE (source):** Audio Lab original PCM playback, route monitor/history and CSV -> `AudioLabViewModel`; no mic and audio stops on screen exit.
- **DONE (source):** Haptic Lab short opt-in vibration, capability snapshot, feedback/history and CSV -> `HapticLabViewModel`; stops on screen exit.
- **DONE (source):** Connectivity Observatory explicit DNS/TCP/TLS probes, history, comparison and CSV -> `ConnectivityLabViewModel`; cancellation on screen exit.
- **DONE (source):** Experiment Lab A/B experiments, user-selected history, annotation CRUD, CSV and share -> `ExperimentLabViewModel`, no replacement of original recorded telemetry.
- **DONE (source):** Compatibility Lab public API probes and aggregate report -> `CompatibilityLabViewModel` with cancellation on leaving the screen.
- **DONE (source):** Hardware Lab sandbox I/O / codecs / routes, bounded motion and network event captures -> `HardwareLabViewModel` with stop/unregister on screen exit.
- **DONE (source):** Gaming Diagnostics public API probes, local SQLite session comparisons, thermal listener and frame pacing -> `GamingDiagnosticsViewModel`; full-screen touch UI stays in Fragment.
- **DONE (source):** Ten full module screens are real Navigation Component `<fragment>` destinations: Game Session, Smart Profiles, Application Center, Experiment Lab, Connectivity Observatory, Audio Lab, Haptic Lab, Compatibility Lab, Hardware Lab and Gaming Diagnostics. The original six hub/game-detail destinations remain. Each moved screen keeps its original Activity class as a thin explicit-Intent compatibility adapter, preserving existing callers, Game Session/Tiles and launcher paths.
- **DONE (source):** Five purpose-specific Android Activity screens stay as centralized `<activity>` destinations through `ActivityNavigator`: MainActivity Kotlin/Rust diagnostics, Game Toolbox overlay UI, Capture Studio MediaProjection consent, Gamepad Live and fullscreen Display Patterns. This is an intentional platform boundary, not a claim that those five screens were converted to Fragment.
- **NOT YET DONE:** physical Android smoke tests (Motorola/Lenovo variant): rotary/configuration changes, selected package navigation, back stack, system picker, haptic/audio stop, sensor/network cancellation and consent behavior.
- **NOT YET DONE:** release signing with the permanent distribution key, merge to main or production release.

CI: historical alpha.4–alpha.27 regression scripts are maintained at the moved source boundary. Both `scripts/check_alpha28_mvvm_backends.py` and `scripts/check_alpha29_navigation_routes.py` run in the Android workflow. A green CI result on an **older** commit is not proof for a newer revision. Run the current head through Kotlin unit tests, Rust compilation, `assembleDebug lintDebug`, APK JNI verification, and signing-safety checks before requesting merge.

Unchanged throughout: package ID, SDK requirements, exact manifest permission allowlist, Rust JNI symbols/cap, SQLite schema v12 and existing data, permanent signer identity, explicit consent and manual-only triggers.

## Navigation handoff matrix (alpha.29 branch revision)

| Route ownership | Screens | Mechanism |
| --- | --- | --- |
| Existing six hub destinations | Home, Games, Tools, History, Settings, Game Detail | Navigation `FragmentNavigator`, retained |
| Ten migrated user-facing modules | Game Session; Smart Profiles; Application Center; Experiment Lab; Connectivity Observatory; Audio Lab; Haptic Lab; Compatibility Lab; Hardware Lab; Gaming Diagnostics | Navigation `FragmentNavigator` using screen ViewModels |
| Five platform-specialized modules | Legacy diagnostics; Game Toolbox; Capture Studio; Gamepad Live; Display Patterns | Navigation `ActivityNavigator`, Activity remains first-class |
| Legacy deep links and feature-to-feature Intents | Ten moved screens | Original exported=false Activity classes host the canonical Fragment; no duplicated UI layout |

The release name and version remain **0.1.0-alpha.27** during this refactor branch; this is not a published `alpha.29` APK. No SQLite migration is performed.

### Completion gates
- Green GitHub Actions for **exact final head commit** (no unresolved failures), followed by code review.
- Device smoke on Lenovo Y70 with Motorola identity: launcher tabs, open/back to Game Detail, Game Session explicit START/STOP, Smart Profiles import/export confirmation, App Center selected-package, Audio/Haptic stop on background, Connectivity cancellation, diagnostics history and save-before-read ordering.
- Confirm media projection remains Android's native one-time consent, overlay privileges remain official Settings intents, no unsolicited network probe or vibration, and no unsigned or CI-temporary-signed APK distributed.
