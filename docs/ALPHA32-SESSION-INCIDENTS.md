# Legion Control · alpha.32 — Session Incidents

## Goal
Add a **real offline episode engine** for historical user-started Game Session recordings, distinct from the transition-only Session Signals and across-session Session Baselines.

## Features
- Reconstruct episodes of Android Thermal Status ≥3, **battery** temperature ≥45 °C, power-saving mode, unvalidated Android network, and display refresh dropping below 90 Hz **after observing ≥90 Hz in the same recording**.
- Count duration only for adjacent **positive-to-positive** measurements separated by at most 45 seconds. Positive-to-negative transitions contribute **zero** unobserved time. A gap, invalid reading or recording end closes an episode without extrapolation.
- Attach at most three explicit USER_MARK entries within 60 s of each episode. This is proximity, **not causality**.
- Per-session filtering, session picker, deep links from History/Game Session/Session Signals/Session Baselines and CSV export via Android Storage Access Framework (SAF).
- Source is existing SQLite v12, no migration, no new permissions, no network calls, no root or Shizuku.
- Bounded read: first 10,000 samples and 2,000 events, retain latest 400 episodes, mark truncation explicitly.
- Each CSV cell is quoted and formula-prefix escaped; report is read only and export is user initiated.

## What is NOT measured
No in-game FPS, frametime, game network ping, CPU/GPU frequency/temperature, or automatic proof of lag causes. Screen Hz is not FPS. Android network validation is not an in-game latency test. Only confirmed sampled durations are reported, not inferred complete episode lengths.

## Test gates
- 15 JVM unit tests for duration, gaps, unknown state, 60 Hz default, >90-to-60 transitions, simultaneous states, markers and safe CSV.
- Static route/manifest/worker checks via scripts/check_alpha32_session_incidents.py.
- Android 35 emulator navigation, selected-session analysis, filtering and back-stack checks, plus existing smoke suite.
- Physical Lenovo Y70 and release signer remain separate validations.

## Compatibility
No SQLite schema change, no deletion of old recordings, no change to Game Session sampling service, no added runtime permissions. Version remains 0.1.0-alpha.27 until a separately signed release is authorized.
