# Legion Control v0.1.0-alpha.47

This prerelease delivers the first functional remediation batch from the Alpha 46 audit (#53). Previous signed Alpha 46 binaries remain unchanged.

## Fixed and improved
- **Smart Profiles:** persistent labels for all profile fields, accessible links between labels and inputs, temperature in real °C (30.0–55.0), strict parsing of dot/comma decimals, and unchanged deci-Celsius SQLite storage.
- **ZIP restore:** local crash-resumable journal. Before SQLite import, a SHA-256 checked settings intent is durably stored. Imported rows and the restore marker commit in one SQLite transaction. Preferences are written only after SQLite commit and can be safely resumed on the next app start or from the Capture Studio manual recovery action. Empty-target policy and ZIP schema validation remain enforced.
- **Connectivity Observatory:** cancel-before-worker-start no longer strands the interface in testing state. Stale history/test results are invalidated when changing the selected diagnostic host.
- **Game Toolbox:** when Android synchronously rejects game launch after a global profile was applied, the app requests compare-before-restore and reports whether any values still need attention.
- **UI:** removed historical Alpha 20–23 version banners from the Connectivity, Application Center, Audio and Haptic screens; updated emulator UI regression steps.
- **Tests:** new Kotlin unit tests for Celsius parsing and network cancellation; expanded real Android instrumentation backup test to simulate interruption after SQLite COMMIT and verify durable recovery.

## Compatibility
- Android package ID: `dev.lolokeksu.legioncontrol`.
- Database schema / user_version: SQLite v12 unchanged; one SQLite header `application_id` value identifies committed ZIP imports.
- Android permissions, Kotlin/Rust JNI ABIs, stored game profiles, existing media files and permanent release signing certificate remain unchanged.
- No Root/Shizuku, unverified OEM controls, background uploads or invented in-game FPS.

## Test evidence and limitations
GitHub Actions must pass the exact merged `main` commit before this signed release is published. The emulator backup interruption test injects a controlled exception at the post-COMMIT boundary; it is **not** a physical power-loss test. MediaProjection recording/AAC, actual vibration and audio, Bluetooth controller, Android ROM-specific permission flows and Lenovo Legion Y70 hardware testing remain **NOT RUN** until tested on real hardware.
