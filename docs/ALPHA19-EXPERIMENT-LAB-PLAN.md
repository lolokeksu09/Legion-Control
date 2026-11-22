# Legion Control alpha.19 — Session Intelligence & Experiment Lab

Status: MERGED + SIGNED alpha.19; automated CI and release passed. Physical-device verification is still outstanding. Plan was committed before code.
Base: `main` @ `6318389f94033857226b7f3b4a24172dd420a640` (alpha.18).
Working branch: `feat/alpha19-session-experiments`.
Target: Android 13+, same package and permanent update signature; public 4PDA, **all games / applications**.

## Product goal

Provide a credible local experimental workflow: annotate real sessions, compare different configurations/conditions, detect missing data and record gaps, and export interpretable results. **No fake FPS, game-server ping, causal proof of performance, CPU/GPU temperature or unavailable OEM switches.** No Root/Shizuku, no new Android permissions.

## Execution order & acceptance

### 0. Plan (first commit)
- [x] Create isolated feature branch from alpha.18 main.
- [x] Commit this plan before implementation.
- [x] Inspect SQLite v7, session/repository/UI, existing version/permission CI assumptions.

### 1. Backend and additive SQLite v8
Files: `ExperimentSchema.kt`, `ExperimentRepository.kt`, `LocalHistoryStore.kt`, `app/build.gradle.kts`.
- [x] New tables: `session_annotations` (one optional note/tag per recorded session), `session_experiments` (named A/B experiment per app), and `session_experiment_members` (assign selected sessions to A/B, stored separately from measurements).
- [x] Foreign keys, indices, bounded strings, deterministic ordering; no deletion of existing sessions/samples/events/profiles. Upgrades v1-v7 -> v8, new installs.
- [x] Transactional create/edit/assign/notes/delete, validate session belongs to experiment's package. Deleting experiment must not delete session history.
- [x] Per-app recent session explorer without `QUERY_ALL_PACKAGES`; old `GameSessionRepository` CSV/ZIP remains.
- Acceptance: actual SQLite v7→v8 migration test with legacy rows + FK and invalid-input cases.

### 2. Pure Kotlin analysis
Files: `ExperimentAnalytics.kt`, `ExperimentQuality.kt`.
- [ ] PARTIAL — Per-session completeness report: valid sampled duration, battery percentage delta and p.p./hour rate **only when enough valid samples, and not interpreted as battery capacity**; minimum/maximum battery temperature; max Android Thermal status; network unvalidated and network changes; sample gap count; incomplete sessions marked explicitly.
- [ ] PARTIAL — A/B report: count, duration and mean valid per-session metrics for each group, exclude missing data from each average; no inferential claims if tiny groups, unequal durations, charging, power-saving or different sample coverage.
- [x] Human-readable annotated report and CSV with formula-injection escaping, no automatic network transmission.
- Acceptance: unit tests with gaps, null/short, charging, noisy network, unequal groups and boundaries.

### 3. User-facing Experiment Lab
Files: `ExperimentLabActivity.kt`, `MainActivity.kt` or `SmartProfilesActivity.kt`, `AndroidManifest.xml`.
- [x] Browse recorded sessions; select one to add/edit note and tag. Dates and device metrics remain original; only annotation changes.
- [x] Create/delete named experiment for specific app, assign recorded sessions to condition A/B manually with explicit labels, review grounded comparison and interpretation limitations.
- [x] Export selected report or full per-experiment CSV through Android Storage Access Framework. Share text only via user intent.
- [x] Session quality report: gaps, missing samples, active recording truncation; clear "data unavailable" vs zero. UI controls must have real callbacks.

### 4. Gates, release and 4PDA documentation
- [x] Update all strict alpha.4–18 version and screen-count regression checks **without removing the checks**.
- [x] `scripts/check_alpha19_experiments.py` runs real old-schema SQLite v7→v8 migration and data-preservation/FK checks.\n- [ ] Add explicit transaction-rollback-on-failure instrumentation beyond the existing FK checks.
- [x] JUnit `ExperimentAnalyticsTest` covers result validity and reports; CI workflow includes them.
- [x] CI testDebugUnitTest, assembleDebug, lintDebug, real Rust targets, signing invariants.
- [x] PR and review; merge only on green CI; trigger main permanent signing and verify SHA-256.
- [x] Deliver finished signed APK in chat and document which device tests are not performed.
- [ ] Manual physical Android installation over signed alpha.18 and tests on Legion Y70; this remains **NOT RUN** in GitHub Actions.

## Risks and non-goals

1. A/B comparisons are **observational**. Different games, battery states, networks or cooling are confounding factors. Never claim measured FPS gain or that a setting causes improvement.
2. Session annotations and A/B conditions are user-entered and may contain private notes. Export only on explicit action; no telemetry network upload.
3. A session might have 0 points, missing battery percent, or be incomplete; never divide by zero, report zero for missing metrics, or silently count interrupted duration.
4. SQLite migrations are additive, and existing pinned release signing/old app ID must be preserved. Never silently clear tables on migration failure.
5. Potential runaway reports: apply bounded queries and limits for on-device/UI reads; full exports can stream.
6. No extra permissions, QUERY_ALL_PACKAGES, Root, Shizuku, foreground-service auto-start or device-performance controls are needed for this patch.

## Evidence log

- Plan: first alpha.19 commit; compare its commit SHA with later code commits.
- Migration: PASS — real SQLite v7→v8 preservation and foreign keys in PR and main CI.
- Kotlin/Rust/Android lint CI: PASS — https://github.com/lolokeksu09/Legion-Control/actions/runs/45004150551 (40 steps).
- Main signing release: PASS — https://github.com/lolokeksu09/Legion-Control/actions/runs/45004150547 (permanent certificate verified).
- Physical device and upgrade-over-release: NOT RUN (required before asserting all-OEM compatibility).

## Signed alpha.19 output — 2026-11-22

- Plan-first commit: `2f02165571611e20f95277021472491d50de07ff`.
- PR #22: https://github.com/lolokeksu09/Legion-Control/pull/22 — merged as `29e2cec00cd93be15a064043dca70825df450182`.
- Permanent-signature artifact: `Legion-Control-v0.1.0-alpha.19-permanent-release-apk` (release workflow run `45004150547`).
- Verified APK SHA-256: `acc051d35d01a4433a7f76c85c104cce12e6ada3951a46f937075e1cf506daed`. Bytes: 3,390,681; ARM64 and x86_64 Rust JNI libraries present.
- Tests NOT RUN: physical Android install/update over alpha.18, actual OEM FGS and recording behaviour, human UI walkthrough, real exporting CSV with notes. The latter require Android hardware/instrumentation.
- Remaining optional deepening: expose maximum Android thermal-status code in Experiment Lab; explicitly aggregate durations and condition balance in A/B summary; add hardware-backed migration/transaction-rollback tests. No fake performance claims.
