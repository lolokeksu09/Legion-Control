# Alpha 53 development — Performance and resource optimization

Base main 5b0b957 / published Alpha 49; independent branch. Alpha 50–52 still pending. No permission, schema, release metadata or Rust ABI changes.

- PERF-01: Hub refresh single-flight, 750ms automatic duplicate window after success, explicit retries coalesce into one follow-up and failures do not enter success cooldown. Equal snapshots do not rebuild screens; Settings re-renders permission state on resume. No indefinite cache.
- PERF-02: launcher classification uses ApplicationInfo included in ResolveInfo, eliminating per-package getApplicationInfo lookups in Hub; existing package visibility and cap preserved. ResolveInfo duplicate packages are removed before label loading. Hub only reads battery level/display Hz, not unrelated current/network/thermal fields.
- PERF-03: exact indexed app_library lookup instead of loading and filtering up to 150 rows; SQL COUNT for Smart Profiles rather than instantiating profile objects. Existing schema and records untouched.
- PERF-04: game and tool search redraw debounced 180ms with pending callbacks removed on rerender/destroy; existing results, 60-row cap and navigation preserved.
- PERF-05: recording callbacks suppress identical consecutive validated-network observations and thermal status. Preserve different network handles/validation transitions and reconnections; manual markers/errors unaffected. Opt-in TCP cadence uses monotonic elapsed clock, first probe retained; sample rate unchanged.
- Verification: four JVM policy cases and two emulator instrumentation cases (real SQLite lookup/count and bounded public launcher/Hub reads), wired into existing Android/SQLite/Rust CI. Source inspection identifies removed redundant calls/allocations, not measured speed or RAM savings. Exact revision build, UI search/permission state, recorder and physical Lenovo profiling remain required. No release claim.
