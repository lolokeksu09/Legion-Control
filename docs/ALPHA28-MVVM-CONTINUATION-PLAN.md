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
