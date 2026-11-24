# Alpha 48 — audit remediation, batch 3

Base: 9c1477e44ae4b793c09ade50a16d311765c184e0 (PR #62). Its Android CI run 45331061295 was still in progress at preparation time. PR #61 previously passed its exact build and emulator checks. This stacked branch does not change the running parent revision.

## Changes

- A48-03: Live Monitor freezes the selected measurement list before launching SAF. ViewModel owns a single pending snapshot across Activity recreation, refuses duplicate requests and new monitoring while a picker or write is outstanding, reports cancellation/launch failure and refuses a result with no retained snapshot after process death. Export worker uses the frozen list. A provider may still leave a partial file on I/O failure; actual picker/provider tests remain open.
- A48-05 (partial): 17 additional explicit feature bindings across Live Monitor, Game Preflight, Session Compare and Session Library; total 26 of 79 stable bindings (including nine Capture Studio controls). Remaining 53 use guarded text fallback. Source contract checks all 26 IDs; search never executes actions.
- A48-08: Live Monitor filters invalid chart/delta readings (temperature, RAM, display refresh, battery), reports charge delta in percentage points and adds latest available-at-last-sample value to chart summary. Session Compare reports actual first/last sample offsets, counts all bounded samples excluded by the selected window or duplicate timestamp, distinguishes percentage-point deltas from percentage shares of samples. Existing CSV columns retain order, seven quality columns appended. Normal samples after the selected window are counted as excluded, not corrupted; comparability gates are unchanged.
- A48-09 (partial): new readForMonitor path reads only system memory and thermal headroom through the existing validation and shared thermal gate, rather than rereading filesystem, processor count and four battery properties during each periodic measurement. General ResourceTelemetryBackend.read remains unchanged. No quantified CPU, RAM or energy gain is claimed without profiling.

## Tests added/updated

- MonitorMetricValidationTest: invalid values and percentage-point semantics.
- SessionCompareAnalysisTest: actual offsets, excluded samples, delta units, report/CSV metadata.
- LiveMonitorInstrumentedTest: retained export snapshot, duplicate refusal, start guard, ActivityScenario.recreate and cancellation/retry. This exercises ViewModel ownership, not the real SAF picker or I/O.
- Updated existing source contracts for the narrow resource API, export ownership and explicit feature IDs.

Status: source changes committed; new Gradle/JVM tests, emulator smoke and physical-device acceptance have not yet run at documentation time. No permission, SQLite schema, signing key, versionCode or package identifier changes. No merge or release. Continue only after required CI; do not independently merge overlapping stacked PRs.

Remaining: full stable-ID migration, real SAF rotation and failure tests, measured CPU/RAM/energy/SQLite profiling, accessibility, physical ROM/media/recovery tests and supply-chain/version consolidation.
