# alpha.31 — Session Baselines (draft, source + runtime checks)

Depends on alpha.30 PR #32; branch PR #33 is stacked and targets alpha.30, not main.

## Actual new functionality
- Same-package history across 7/30 days, read from SQLite v12 with bounded DAO queries.
- No duplicate Experiment Lab A/B experiments: automatic *observational* comparison of
  latest recording with at least three **previous** comparable recordings.
- Existing ExperimentAnalytics.evidence supplies valid battery discharge and temperature.
- Strict exclusions: unknown/incomplete session, session under 2 minutes,
  fewer than 6 readings, samples cut at 2,000, gaps over 45 seconds, coverage under 70%,
  missing start/end coverage, power saving, charging/increasing battery percentage
  or missing temperature. Excluded rows and reasons stay visible.
- Median from previous sessions (not including latest) for peak battery temperature
  and observed percentage-point discharge/hour. Simple elevated-value signals only
  when battery peak >= median + 3 C, or discharge >= median + 1.5 pp/h **and** 25%.
  This is not a diagnosis or FPS/performance regression.
- Max 24 sessions, max 2,000 samples/session; local, deterministic, bounded.
- New SessionBaselinesViewModel and SessionBaselinesFragment with package picker,
  7/30 day windows, explicit SAF CSV export, share and link to Session Signals.
  History, Game Session and Session Signals routes use the NavHost/back stack.

## Verification
1. Nine JUnit tests for median reference selection, missing values, short sample
   windows, gaps, incomplete/charging, exact game, thresholds and CSV escaping.
2. Static regression script check_alpha31_session_baselines.py.
3. GitHub CI: Kotlin/Rust, lint, Android APK with JNI, signing guard.
4. Android 35 emulator: History -> Session Baselines -> 30 days -> Session Signals
   plus pre-existing manual recorder, manual markers and all older module paths.
5. Physical Lenovo Y70 and permanent signing: **NOT RUN** until tested.

No new database tables/migrations, no root/Shizuku/privileged controls, no
automatic network probes, no claims about FPS or game-server latency. Does
not change the existing user-started foreground recording behavior.
