# Legion Control alpha.24 — Game Toolbox (functional overlay)

**Baseline:** main `9f5cd0a063403252c4a3d729aad72c20b04944b8` (alpha.23). This plan is the first commit on `feat/alpha24-game-toolbox`, before implementation. Version target `0.1.0-alpha.24` / `versionCode=24`. Preserve `dev.lolokeksu.legioncontrol`, permanent release signer, Rust JNI, all SQLite v12 data.

## Decision after repo audit

Alpha.14–23 already contain diagnostic labs, Smart Profiles' manual audio/DND/brightness/timeout controls, Application Center's curated app library and launcher, game session recording, and Haptic Lab. Another test page or simple favorites list would duplicate them. Instead: make existing **public Android system actions accessible during other foreground apps** via an explicitly opted-in, stoppable floating toolbox. This is a new cross-app workflow, not a performance booster or game telemetry feature.

## User outcomes / acceptance

- **REQ-24-01 Permission & control:** New Game Toolbox Activity explains Android overlay permission and provides official `ACTION_MANAGE_OVERLAY_PERMISSION` settings. Service starts **only on user press**, if `Settings.canDrawOverlays` is true. Failure is visible; no auto-start, boot receiver, accessibility, Shizuku, Root or background scanning.
- **REQ-24-02 Floating overlay:** Movable compact bubble, tap to expand/collapse, preserve bubble position and user-selected opacity/size; use `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE`, touch only its own bounds; it must not intercept entire game screen, read game inputs, or obscure lock screen deliberately. Show finite foreground notification with STOP button; service returns `START_NOT_STICKY`, and removing bubble/notification on stop.
- **REQ-24-03 Useful quick actions:** In overlay: media volume down/up using public `AudioManager`, explicit DND on/off only with Android notification-policy special access, brightness 50%/80% and manual restoration using existing `SmartSystemControls` and write-settings access, open Legion Control, close toolbar. Display failures/permissions in toolbar, never falsely say an OEM action succeeded.
- **REQ-24-04 Game launcher:** Choose a visible installed launcher app using existing `MAIN/LAUNCHER` visibility (no `QUERY_ALL_PACKAGES`); persist selection in app-private preferences. User button starts overlay then launches selected app; overlay itself offers reopening that selected app. A missing/invisible launcher fails cleanly.
- **REQ-24-05 Privacy, compatibility & migration:** One new special permission `SYSTEM_ALERT_WINDOW` (must be explicitly granted in Settings), reuse existing declared `FOREGROUND_SERVICE_SPECIAL_USE`, `ACCESS_NOTIFICATION_POLICY`, `WRITE_SETTINGS`, `MODIFY_AUDIO_SETTINGS`; no new SQLite migration and no Internet requests. No claim of CPU/GPU, FPS, OEM touch boost, system-wide timer accuracy or auto DND/brightness restoration. All system changes are one-tap manual; if brightness is changed, existing restore action works.
- **REQ-24-06 Verification:** pure Kotlin test for safe toolbox presentation values and usable defaults; static manifest/lifecycle/policy tests; `assembleDebug`/lint/whole Kotlin+Rust CI/signature gates without weakening existing checks. Physical Android 13–16 and Motorola ROM overlay/interaction/notification/access smoke test remains a separate gate and must not be marked passed by CI.

## Sequence

1. Commit this plan, create the isolated Game Toolbox screen, preference validation and manually started overlay service.
2. Wire activity+service into manifest and main menu; bump version; add explicit permission and clear failure/restore UX.
3. Add JVM and static regression tests; wire CI and docs. Do not change previous SQLite schemas, Haptic Lab, Rust or release certificate.
4. PR, inspect CI, correct errors, then only merge/release after mandatory checks and existing authorization.
5. Document what the CI proved and what still needs on-device checks.

## Risks / mitigations

- `SYSTEM_ALERT_WINDOW` has broad UI implications: request only from visible Activity, no keylogging, screenshot capture or global transparent click layer; show an explicit stop control and persistent foreground notification. Android/OEM overlays can be hidden/denied.
- Android 14+ foreground-service restrictions: explicitly use the existing special-use FGS permission and declare special-use subtype; launch from foreground Activity, no background start.
- DND, brightness, volume affect global device state; never apply them on automatically opening a game. Keep controls manual and offer restore, with reliable "requires permission" states.
- Only read Android public platform APIs. No secret storage or external analytics.

## Execution status

- [x] Repository and PR #26 audited.
- [x] Plan committed before implementation.
- [ ] Code implemented.
- [ ] JVM/static/CI gates passed.
- [ ] Physical device tested (requires user device).
