# Live Monitor — Alpha 48 feature batch

## User workflow
Open **Live Monitor** from the Home or Tools tab. Select 2/5/10-second interval, start collection explicitly, switch the graph between temperature, available RAM, display refresh and battery. Pin a comparison point, stop the monitor, optionally export the complete sample run as CSV through Android's document picker.

## Backend contract
- GameSessionTelemetry and ResourceTelemetryBackend are reused without changing their APIs. No CPU/GPU clocks or synthetic FPS are claimed. RAM is device-wide available memory, not game-process memory; thermal status/headroom are Android/OEM signals, not CPU temperature.
- The executor reads outside the main thread, one sample at a time. Monotonic elapsed times are recorded when the worker starts its measurement. A generation rejects results from a stopped run. onStop invalidates the current run, cancels its work and removes scheduled callbacks. A resumed screen requires an explicit restart.
- 120 samples maximum, 2/5/10-second intervals, no background service, new permissions, network probes or system writes. Unsupported fields stay null and graph gaps are not interpolated.
- Baseline and samples are process-local. Rotation retains the ViewModel data but stops collection; process death loses the run. New runs require confirmation when replacing samples. CSV is explicitly exported; no SQLite migration or change to the existing backup format.

## Acceptance evidence
- JVM LiveMonitorPolicyTest: cap, monotonicity, allowed intervals, null-aware deltas and CSV quoting.
- Android LiveMonitorInstrumentedTest: actual periodic readings, comparison pin, Activity stop and no implicit restart.
- Android UI smoke: open from Home, idle → start → actual sample → stop → pin.
- Static check_live_monitor.py guards permission, lifecycle and source contracts.

Only exact-head CI and subsequent physical Lenovo Legion Y70 testing establish runtime/device evidence. This development patch is not a published signed APK yet.
