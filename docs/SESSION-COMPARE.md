# Session Compare

Stacked on Game Preflight PR #57, revision e7a15934. Published Alpha 47 remains unchanged.

## Workflow
Open Session Compare from History, Tools, a game detail or Game Session. Pick one recorded package, then two distinct completed records A and B from its latest 25 sessions. Select the first 5, 10, 15 or 30 minutes of each session. Swap A/B to change the sign of B minus A. No new samples or recorder are started.

Compare seven observations: peak battery temperature, endpoint temperature change, battery drop in percentage points, median display refresh, and fractions of known samples reporting severe thermal status, power saving or unvalidated network. Three dual-series graphs use the same elapsed time window and shared y-axis, with min/max accessible text and breaks at unknown data or gaps over 45 seconds.

## Limits and interpretation
Read up to 2001 samples per session; analyze at most 2000. Remove duplicate timestamps and readings outside the selected window; calculate measured coverage only from adjacent samples no more than 45 seconds apart. Require a completed USER_STOP record covering the entire selected window, 6+ readings, 70% coverage and observed endpoints within 45 seconds. A metric also needs at least 3 known values and 70% known-sample coverage in each recording before reporting B−A. Partial observations remain visible but no unsupported delta is produced. Fractions are percentages of known samples, not fractions of time. Changes of battery charge include possible charging. Display Hz is not game FPS, and Android network validation is not game ping. Same duration is not proof of identical workload or causal improvement.

Read-only SQLite v12; existing sessions and profiles unchanged. Export the selected result explicitly through Android SAF as CSV or share plain text. No new permissions, new database or background service. Up to 40 packages and 25 recent records per package, per existing repository bounds.

## Verification
JVM tests cover aligned windows, invalid pairs, gaps, short and interrupted sessions, unknown fields, duplicate timestamps and CSV. Emulator-only instrumentation seeds isolated synthetic SQLite fixtures and verifies selection, A/B sign reversal, invalidation, insufficient 15-minute coverage and no live recording. This fixture test verifies analysis and repository wiring, not actual game measurements. Emulator UI smoke opens the screen via History. Static guard checks existing permissions and read-only integration. Exact-head CI and physical device tests remain pending.
