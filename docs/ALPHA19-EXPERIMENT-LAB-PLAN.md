# Legion Control alpha.19 — Session Intelligence & Experiment Lab

Status: PLAN-FIRST — created before any alpha.19 application code.
Base: `main` @ `6318389f94033857226b7f3b4a24172dd420a640` (alpha.18).
Working branch: `feat/alpha19-session-experiments`.
Target: Android 13+, same package and permanent update signature; public 4PDA, **all games / applications**.

## Product goal

Provide a credible local experimental workflow: annotate real sessions, compare different configurations/conditions, detect missing data and record gaps, and export interpretable results. **No fake FPS, game-server ping, causal proof of performance, CPU/GPU temperature or unavailable OEM switches.** No Root/Shizuku, no new Android permissions.

## Execution order & acceptance

### 0. Plan (first commit)
- [x] Create isolated feature branch from alpha.18 main.
- [x] Commit this plan before implementation.
- [ ] Inspect SQLite v7, session/repository/UI, existing version/permission CI assumptions.

### 1. Backend and additive SQLite v8
Files: `ExperimentSchema.kt`, `ExperimentRepository.kt`, `LocalHistoryStore.kt`, `app/build.gradle.kts`.
- [ ] New tables: `session_annotations` (one optional note/tag per recorded session), `session_experiments` (named A/B experiment per app), and `session_experiment_members` (assign selected sessions to A/B, stored separately from measurements).
- [ ] Foreign keys, indices, bounded strings, deterministic ordering; no deletion of existing sessions/samples/events/profiles. Upgrades v1-v7 -> v8, new installs.
- [ ] Transactional create/edit/assign/notes/delete, validate session belongs to experiment's package. Deleting experiment must not delete session history.
- [ ] Per-app recent session explorer without `QUERY_ALL_PACKAGES`; old `GameSessionRepository` CSV/ZIP remains.
- Acceptance: actual SQLite v7→v8 migration test with legacy rows + FK and invalid-input cases.

### 2. Pure Kotlin analysis
Files: `ExperimentAnalytics.kt`, `ExperimentQuality.kt`.
- [ ] Per-session completeness report: valid sampled duration, battery percentage delta and p.p./hour rate **only when enough valid samples, and not interpreted as battery capacity**; minimum/maximum battery temperature; max Android Thermal status; network unvalidated and network changes; sample gap count; incomplete sessions marked explicitly.
- [ ] A/B report: count, duration and mean valid per-session metrics for each group, exclude missing data from each average; no inferential claims if tiny groups, unequal durations, charging, power-saving or different sample coverage.
- [ ] Human-readable annotated report and CSV with formula-injection escaping, no automatic network transmission.
- Acceptance: unit tests with gaps, null/short, charging, noisy network, unequal groups and boundaries.

### 3. User-facing Experiment Lab
Files: `ExperimentLabActivity.kt`, `MainActivity.kt` or `SmartProfilesActivity.kt`, `AndroidManifest.xml`.
- [ ] Browse recorded sessions; select one to add/edit note and tag. Dates and device metrics remain original; only annotation changes.
- [ ] Create/delete named experiment for specific app, assign recorded sessions to condition A/B manually with explicit labels, review grounded comparison and interpretation limitations.
- [ ] Export selected report or full per-experiment CSV through Android Storage Access Framework. Share text only via user intent.
- [ ] Session quality report: gaps, missing samples, active recording truncation; clear "data unavailable" vs zero. UI controls must have real callbacks.

### 4. Gates, release and 4PDA documentation
- [ ] Update all strict alpha.4–18 version and screen-count regression checks **without removing the checks**.
- [ ] `scripts/check_alpha19_experiments.py` runs a real old-schema SQLite migration and data-preservation/transaction tests.
- [ ] JUnit `ExperimentAnalyticsTest` covers result validity and reports; CI workflow includes them.
- [ ] CI testDebugUnitTest, assembleDebug, lintDebug, real Rust targets, signing invariants.
- [ ] PR and review; merge only on green CI; trigger main permanent signing and verify SHA-256.
- [ ] Deliver finished signed APK in chat and document which device tests are not performed.
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
- Migration: NOT RUN
- Kotlin/Rust/Android lint CI: NOT RUN
- Main signing release: NOT RUN
- Physical device and upgrade-over-release: NOT RUN
