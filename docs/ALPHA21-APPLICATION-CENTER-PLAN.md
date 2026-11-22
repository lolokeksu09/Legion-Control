# Legion Control v0.1.0-alpha.21 — Application Control Center

Status: PLAN-FIRST. Baseline `main`: `c1e72a44f849ce265a0afcc38b252fe8f657a9ff`, alpha.20. Android 13+; all launchable apps and games, not Wild Rift-specific. Package `dev.lolokeksu.legioncontrol`, pinned permanent release certificate retained.

## Outcome

Provide a practical, privacy-respecting local app library with custom favorites/categories/notes; on-demand package metadata, requested permission state, install/update detection; safe shortcuts into stock Android app-management screens; optional Android Usage Access trends and CSV export.

**Hard boundaries:** No root, Shizuku, QUERY_ALL_PACKAGES, Accessibility, Device Admin, automatic app kill/uninstall, AppOps overrides, setting other apps' permissions, claims of real-time app CPU/GPU usage, or hidden background scanning. Launcher discovery uses existing `<queries> MAIN/LAUNCHER` visibility; user may manually enter package names but Android may not expose invisible packages.

## Plan committed before feature code
- [x] Create branch `feat/alpha21-application-center` from main.
- [x] Commit this file as first commit; later code commits reference it.
- [ ] Review existing `LocalHistoryStore`, `GameUsageDiagnostics`, `SmartProfilesRepository`, `MainActivity`, manifest and alpha.20 CI checks.

## Phase 1 — backend (priority)
- [ ] SQLite v10 additive `app_library` and `app_audit_events` tables. v1–9 migrations preserved, no destructive drop; FK event relation to tracked apps with CASCADE only within new tables.
- [ ] `ApplicationCenterRepository.kt`: CRUD favorite/category/notes/alias; track/untrack; persist manual metadata snapshots and detect update, disappearance/reappearance, same version stable (no duplicate event); query recent events; user-exported CSV with formula injection prevention. Scan only tracked apps on explicit user action.
- [ ] `ApplicationInventory.kt`: discover visible launcher apps using `queryIntentActivities`, package details (version, longVersionCode, firstInstallTime, lastUpdateTime, system-app flag), declared runtime permission states only for a visible package, notification settings link, Android app info link, safely launch app. Unsupported/invisible = unknown, not uninstalled proof; handle OEM SecurityException gracefully.
- [ ] `ApplicationUsageInspector.kt`: optional `UsageStatsManager` for 1-day / 7-day foreground totals only if explicit Usage Access; otherwise show permission required; don't pretend match/game stats.
- [ ] Pure Kotlin `ApplicationAuditRules.kt`: version-change classifier and clear unknown/possibly invisible state; dedup; event classification tests.

## Phase 2 — UI (after backend/tests)
- [ ] `ApplicationCenterActivity.kt` AMOLED, launcher-app picker/search, tracked library and favorites, favorite/category/notes/alias forms and explicit tracking actions.
- [ ] Controls: inspect permissions/metadata, manually refresh tracked status; view changes; 24h/7d usage stats if special access; open Android stock app info, notification settings, battery optimization settings; launch selected app; open Smart Profiles and Experiment Lab by package.
- [ ] CSV export via `ACTION_CREATE_DOCUMENT`; explicit deletion confirmation; clear unknown/inaccessible states.
- [ ] Link from `MainActivity`, declare non-exported Activity in `AndroidManifest.xml`; no new Android permissions.

## Phase 3 — verification and release
- [ ] Add real SQLite v9→v10 migration/constraint/legacy history check `scripts/check_alpha21_application_center.py` and wire both CI workflows.
- [ ] Kotlin JVM tests of change classifiers/formatting. Preserve all prior alpha.4–20 verification; update strict version and top-level button count assertions only where necessary.
- [ ] Bump to versionCode 21, versionName `0.1.0-alpha.21`; preserve signing and ABIs.
- [ ] Build Kotlin/Rust, run testDebugUnitTest, assembleDebug, lintDebug, verifier and signing negative gates in GitHub CI; fix errors and rerun.
- [ ] PR and merge to main only after passing CI; verify permanent-signed APK in release workflow and make the verified file available in chat.
- [ ] Physical update test on Lenovo Legion Y70 / Motorola hybrid firmware, app visibility and permissions, usage access, export/import, upgrade over alpha.20. **NOT RUN** until actually executed on hardware.

## Safety & privacy
- User notes/categories stored in private SQLite; no server transmission except app launch/system settings actions.
- Permission visibility and Usage Stats are incomplete by Android design. Treat unknown as unknown, never proof an app has been removed or permission revoked.
- System app info and notification settings are just official Android Settings deep links; no privileged writes or background automation.
- UPDATE events use observed package update time/version transitions and are **not** proof of a precise install event.
- No API available to silently force-max screen Hz, game FPS, change other apps' CPU policy or app permissions; not promised.

## Release gates
- A first-commit plan: PASS on file creation.
- B real SQLite migration: NOT RUN.
- C JVM rules and Android build: NOT RUN.
- D 4PDA signed APK: NOT RUN.
- E device hardware proof: NOT RUN.
