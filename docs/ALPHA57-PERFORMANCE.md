# Alpha 57 — performance baseline and bounded optimization

Base main cacc851 (same source tree as successful Alpha 56 CI 45426982959). Main CI 45439439985 failed during NDK download/unzip (not compilation); signed publication 45439440041 refused because Android CI failed. SDK install retries three times without changing pinned versions, permissions or validation.

PERF-01: CI smoke captures performance-home.json on Android 35 emulator: three process-cold starts (am start -W TotalTime), settled Home PSS (KiB), 5-second process CPU ticks and percent of one core only when device CLK_TCK is available. Each launch must reach real Home. APK hash, samples and errors retained; no fabricated zero, no performance thresholds for hosted runners. Cold process is not cold storage; first draw is not full interactive readiness. Debug emulator only, not proof of Legion hardware performance.

PERF-02: Games and Tools skip unrelated Ready-to-Ready rebuilds, preserving focused search, pending debounce and row objects. Games still rerender on app list/favorite changes, both on warning/loading/error changes; explicit user actions render normally. Other tabs unchanged. JVM policy test covers decisions; existing UI smoke checks search/navigation.

PERF-03: baseline is initial Alpha 57 measurement; no before/after speedup claimed. For comparison run identical script/device/build type against both source revisions. CPU may be unavailable under Android proc access restrictions; explicit partial status, no silent success. No new permissions, database migrations or dependency additions. Release metadata remains Alpha 49; publication not requested.
