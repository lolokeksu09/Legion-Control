# Alpha 57 — Performance Profiling

Base Alpha 56 3e09e2c. Existing Android 35 smoke is extended, no APK code/permission/version/DB change.

PERF-01: measure actual main-process memory (PSS, RSS, Java/native heap PSS, swap PSS where Android reports them) using dumpsys meminfo PID. Take observations on Game Toolbox, Hardware/Battery, Connectivity and Home after a one-second settle and five-second idle window. PID must remain identical; missing fields stay null. This is idle screen memory, not active probe/game workload, not system RAM.

PERF-02: debug run-as /proc/PID/stat user+system ticks, same process starttime and monotonic host window. Convert to percent of one core only if device getconf CLK_TCK is available; otherwise retain raw ticks/rate. Android dumpsys cpuinfo optional and separate (different sampling window). No assumption of 100 Hz or zero if inaccessible; percent may exceed 100 for multiple cores. ADB and emulator overhead apply.

PERF-03: timestamp actual ADB tap to observed UI hierarchy marker for the three selected modules, not layout-only timing; includes ADB, polling and fixed UI wait. Three process-cold starts with existing data, am start -W TotalTime/WaitTime/LaunchState and observed Home readiness recorded separately. No clear/reset in profiler; force-stop restricted to existing disposable CI at end after session stop. Not first-install or release timing.

PERF-04: incremental JSON evidence plus raw meminfo/startup output in existing smoke-evidence artifact. Record exact Git SHA, APK SHA-256, SDK/ABI/fingerprint and screen. Median valid cold startup samples; no arbitrary emulator performance thresholds or unsupported physical conclusions. Do not compare different hardware/build variants/conditions as causal. For real A/B, run same protocol on exact baseline and candidate, same device and build type.

PERF-05: parser fixtures integrated into canonical source checks; CI runtime measurements pending. Physical Lenovo and comparative baseline not measured. Existing UI and instrumentation gates retained. No signed APK or release claim.
