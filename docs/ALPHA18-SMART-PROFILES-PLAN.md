# Legion Control v0.1.0-alpha.18 — Smart Profiles & Automation
Status: IMPLEMENTATION PLAN (created before feature changes)
Baseline: main 4e960764c418c9d23fc818cf8215305eda84e291 (alpha.17).
Target: Android 13+ (minSdk 33), targetSdk 36, no Root/Shizuku, universal Android devices and games, suitable for public 4PDA distribution.

## Outcome

Turn read-only diagnostics into an explicit user-controlled per-app workflow: saved app profiles, local rules and warnings, sensible public-API quick controls, a home for session insights and a minimal Quick Settings entry. All existing alpha.17 functionality and saved histories must survive the update.

## Non-negotiable contracts

- Backend and persistence first, tests and integration next, interface last; no deceptive switches, fabricated FPS, "gaming ping", CPU/GPU thermometers, OEM/Bypass/Touch Boost, or game-specific assumptions.
- Recordings remain opt-in via explicit foreground action; never auto-start a foreground service silently or at boot. A profile does **not** start a recording automatically merely because a game is foreground.
- Strictly limit Android permissions. No `QUERY_ALL_PACKAGES`, location, Accessibility, root, Shizuku, usage-stats hard dependency, covert VPN or network interception. Optional device settings controls must check and explain Android permissions before attempting a change.
- Never send profile names, samples, network observations or backups to any server; never export or import anything without a system document picker.
- Preserve the existing `applicationId`, pinned release-signing fingerprint, native Rust ABIs, and monotonically increment versionCode to 18.
- Make unknown or API-blocked state visible, not zero. Resource usage: sample interval unchanged (15s), no high-frequency polling or forced wake locks. A notification is never proof that an OEM won't kill a service.

## Phase 0 — create plan and isolated branch (required first)

- [x] Create `feat/alpha18-smart-profiles` from alpha.17 main, and commit this file **before** feature changes.
- [ ] Review current `GameSessionActivity.kt`, `GameSessionRepository.kt`, `GameSessionRecorderService.kt`, `LocalHistoryStore.kt`, `AndroidManifest.xml`, CI verification scripts and `app/build.gradle.kts`.

## Phase 1 — data and rules backend (P0)

Paths: `SmartProfilesSchema.kt`, `SmartProfilesRepository.kt`, `SmartRules.kt`, `LocalHistoryStore.kt`.

- [ ] Add SQLite v7 tables `smart_profiles`, `smart_rules`, `smart_rule_events` with non-destructive v1-v6 migrations, FK constraints, useful indexes, length limits, and uniqueness of app packages.
- [ ] CRUD profiles (label, package, sampling-related preferences, optional per-profile thresholds), and rules (battery temperature, battery percentage, Android thermal status, power saver, network validation/transport).
- [ ] Pure Kotlin rule evaluator with no side effects, explicit unknown-state behavior and validation ranges; event dedup/cooldown to prevent notification spam.
- [ ] Add a repository/API layer for recent sessions per package and aggregates. Separate real measurements from derived indicators. No fake global 'performance score'.

Acceptance: SQL constraints + migration test against actual v6 fixture; previous alpha.17 session and sample rows unchanged; evaluator unit tests cover boundary, null, duplicate, cooldown, cross-game rules.

## Phase 2 — opt-in actions and recording integration (P0)

Paths: `GameSessionRecorderService.kt`, `GameSessionActivity.kt`, `SmartProfilesActivity.kt` (new).

- [ ] User can save/select a profile, then explicitly start recording for that app; retain manual package input.
- [ ] Evaluate enabled rules on existing recorded snapshots, persist event transitions with timestamps, display warnings and optionally post a notification (permission-gated, bounded).
- [ ] Explicit user controls: volume through public AudioManager, DND only with Notification Policy access; brightness/display timeout only if WRITE_SETTINGS granted. Never bypass user consent; consider restoring/undoing the last user change.
- [ ] Keep recorder's existing notification stop action and cancellation semantics. Do not invent continuous access to other apps' metrics.

Acceptance: a profile works without Usage Access, Root and Shizuku; denied permissions cause explained no-op; no duplicated event storms.

## Phase 3 — insights, network and local backup (P1)

Paths: `SmartSessionInsights.kt`, `SmartNetworkLab.kt`, `SmartProfilesBackup.kt` and dedicated UI.

- [ ] Local aggregates by package / 24h / 7d / 30d for sample count, duration, battery delta and temperature; disclose interrupted sessions and incomplete records.
- [ ] Manual network probes only with explicit tap, distinguishing DNS, TCP connect and Android NetworkCapabilities; never label as game-server ping. Avoid scanning LAN or leaking SSIDs/IPs.
- [ ] JSON export/import via ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT; validate version, size and ranges; transactional import with duplicate merge and rollback; old saved measurements never deleted.

Acceptance: import exported profiles and rules on a fresh install, reject invalid/malicious files, no data loss on duplicates.

## Phase 4 — Quick Settings & compatibility UX (P1)

Paths: `LegionQuickTileService.kt`, `SmartCompatibilityBackend.kt`, `AndroidManifest.xml`.

- [ ] Opt-in Quick Settings Tile with status and safe launcher to Smart Profiles. Respect Android 14/15 restrictions on background activity launches; do not start FGS invisibly from a tile.
- [ ] Capability matrix: available / user permission required / Android/OEM restricted / unknown. Display truthful API gates for DND, WRITE_SETTINGS, notifications, usage stats, NetworkCapabilities and background recording.

Acceptance: no root dependency; no WRITE_SECURE_SETTINGS; clean handling of unsupported features and tile service lifecycle.

## Phase 5 — verification, release & 4PDA package

Paths: `app/src/test/...`, `scripts/check_alpha18_smart_profiles.py`, `.github/workflows/android.yml`, `.github/workflows/release-apk.yml`, `docs/...`, `README.md`.

- [ ] Tests: pure rules/aggregates, SQLite real 6→7 with history preservation, permission/manifest/static contracts, backup round trip.
- [ ] CI: unit tests, debug assemble, lint, native Rust builds, signing guard and release-certificate fingerprint; keep all previous alpha.4–17 CI checks valid, updating only strict version/permission assumptions.
- [ ] PR review, merge only after green CI. Trigger main signing workflow; verify the permanent certificate and APK versionCode 18. Provide downloadable APK and 4PDA changelog only if a real verified artifact exists.
- [ ] Physical test required on Lenovo Legion Y70 (Motorola firmware) plus preferably a separate OEM: actual FGS behavior, DND permission flow, profiles, import/export, wake/sleep, notification actions, installation over alpha.17.

## Risks and explicit limitations

1. Android DND and WRITE_SETTINGS are protected by user-controlled special access; without them those controls do nothing. DND policy access must be rechecked on every call.
2. Android only reports battery temperature; thermal status is not a CPU/GPU thermometer. The screen mode refresh rate is **not** in-game FPS.
3. Android 14+ imposes foreground-service start limits. The existing user-started specialUse service must stay declared and visible.
4. `QUERY_ALL_PACKAGES` is intentionally avoided: the app chooser only lists launchable apps visible through `queries MAIN/LAUNCHER`. Some system apps cannot be selected/opened.
5. Rules are triggered only while a user-initiated recording exists; no device-wide stealth surveillance, implicit automation or infinite background jobs.
6. Large backups and history require capped memory/size, transactions and integrity checks. No destructive migration, no silent wipe.
7. A green CI cannot prove real OEM hardware compatibility; document which checks are NOT RUN.

## Review gates

- Gate A — plan file exists on GitHub before code (pass/fail)
- Gate B — schema v7 migration verified on actual SQLite (pass/fail)
- Gate C — rules and profile UX exercised by JVM/instrumentation where possible (pass/fail/not run)
- Gate D — Android debug + Rust + lint green (pass/fail/not run)
- Gate E — permanently signed release APK built and fingerprint-verified (pass/fail/not run)
- Gate F — physical Android device interaction tested (pass/fail/not run)
