# Legion Control alpha.30 — Session Signals backend

## Scope
New functional module implemented on top of alpha.29 Navigation + MVVM.
This branch targets \`refactor/alpha28-rust-sampling-mvvm\` (PR #31), not main.

- New manual **USER_MARK** event while Game Session foreground service is actually recording.
  A mark never starts a recording. Blank input is rejected and notes are bounded to 120 characters.
- Read up to 50 recent session headers, 10,000 samples and 2,000 historical events
  for a chosen session, sorted chronologically.
- Pure Kotlin event correlator detects thermal severe/recovery, battery temperature
  threshold crossings, power-saving observations, Android network validation and
  transport changes, display refresh-rate shifts and sample gaps over 45 seconds.
  Missing fields and gaps never imply a recovery. No false gaming FPS/ping claims.
- New Fragment + ViewModel with native Navigation Component routes from History and
  Game Session (including legacy Activity fallback), bounded text and manual CSV export
  using Android Storage Access Framework. Share uses explicit system chooser.
- Existing SQLite schema **v12 stays unchanged**; stored points are never rewritten.
  No new permissions, root, Shizuku, hidden APIs or automatic network probes.

## Verification gates
1. \`scripts/check_alpha30_session_signals.py\` — module structure, consent/safety constraints.
2. \`gradle --no-daemon testDebugUnitTest\` — seven event engine regressions on real Kotlin.
3. \`bash scripts/build-native.sh\` and \`gradle --no-daemon assembleDebug lintDebug\`.
4. Android 35 x86_64 emulator smoke: navigation, explicit mark, stop and timeline.
5. Real Lenovo Android 13 and permanent distribution signature remain NOT RUN.

## Limitations
- Package label comes from user's selection, not independently verified game foreground.
- Current/thermals/refresh Hz are Android system readings, not per-game measurements.
- Analyzer truncates at database read caps and displays this explicitly.
- Event chronology is not proof of a causal connection between conditions and gameplay.
- No main merge or official release until physical hardware and signing checks pass.
