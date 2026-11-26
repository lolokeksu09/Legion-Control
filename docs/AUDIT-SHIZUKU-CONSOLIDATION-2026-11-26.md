# Legion Control — consolidation and Shizuku audit (2026-11-26)

Baseline: d689ada0bd7cc20bfdb6d08493a35335e373519d (stacked PR #88 including #87). Owner authorizes optional Shizuku, autonomous big patches, main integration and signed APK; optional lengthy tests only. Existing storage/signing/ABI preserved.

| ID | Evidence / issue | Decision / acceptance |
|---|---|---|
| LC-S01 | Settings had four repeated module links plus Capture Studio clip library under backups; Tools omitted core game modules | Canonical HubToolCatalog, 19 distinct Activity routes, Tools browse/search, Settings keeps backup/recovery/permissions and one catalog link; no functionality removed |
| LC-S02 | HubPageFragment built local CatalogAction closures on each render | Immutable registry and word-wise search, preserve FeatureFinder anchors and HubDestinationRoutes |
| LC-S03 | Permission center uses one row per capability, but status and title occupy two lines | Compact shared title/status hierarchy; static restrictions preserved, explicit actions and TalkBack labels |
| LC-S04 | No Shizuku SDK, generic previous prohibition | Official api/provider 13.1.5, protected provider, package query, explicit permission, lifecycle listeners, read-only remote permission report, UID 2000 only; denial/disconnect/unknown visible; no automatic mutations |
| LC-S05 | Home triple nested summary surfaces | One summary surface, unchanged real counts and clip-window caveat |
| LC-S06 | Full emulator blocks every everyday iteration | Manual full_emulator switch; ordinary CI retains unit/source/SQLite/Rust/lint/APK/signature/SBOM gates; skipped runtime checks not passed |
| LC-S07 | Gaming Lab, Hardware Lab, Compatibility Lab, Diagnostics and Live Monitor overlap in battery/network/hardware presentation | Next: map each actual operation and producer, keep unique diagnostics, use single measurement repository and route aliases before removing duplicate UI; no data/schema deletion |
| LC-S08 | Multiple profile screens own similar launch/manual controls | Next: separate one-shot launch profiles, threshold rules and system settings ownership; shared service/state, preserve compare-before-restore |
| LC-S09 | Capture and session history have separate storage/exports and controls | Next: common media/history navigation, retain typed SQLite and MediaStore contracts; no indiscriminate merging |
| LC-S10 | Physical performance and Shizuku availability vary by ROM | NOT RUN on physical Lenovo Y70; no fabricated FPS, PSS or privileged function claims |

Official integration reference: https://github.com/RikkaApps/Shizuku-API (Guide, protected provider, binder lifecycle, permission requests, UID and checkRemotePermission). SDK is fixed to 13.1.5. The initial Shizuku integration performs actual authorized Binder reads; no shell command execution, no hidden API or root. A connected service is not proof of available game FPS/Touch Boost/Bypass. Existing public Android controls work independently.

The initial code patch addresses LC-S01–06; LC-S07–10 remain explicitly open. User data is not deleted. Runtime rendering and physical Shizuku behavior require device verification.


## LC-S11 — read-only background policy through Shizuku

Explicit Settings action opens a non-daemon UserService using the official API. Fixed read-only probes: cached_apps_freezer, DeviceConfig use_freezer, low_power, app_standby_enabled, adaptive_battery_management_enabled. UID 2000 checked on both sides; no caller-supplied arguments, writes, arbitrary command interface or root. Each subprocess has a 1.5 s timeout and limited normalized output; parent operation times out after 20 s, closes on disconnect or leaving Settings. Configuration values are not proof of effective OEM behavior. Missing values are not represented as disabled. Two JVM parser tests and source guards. Physical execution NOT RUN.

## LC-S12 — Game Hub and Toolbox consolidation

One durable game-selection path for Hub and Toolbox, serialized against ZIP restoration. Game detail selects then opens Toolbox only after successful commit on worker and only while originating destination remains active. Shared action tile instead of duplicate renderer, real app icon when visible, grouped launch/history/media actions, library pagination scrolls to count, redundant four global shortcuts replaced with catalog link. Existing routes, local history and favorites retained. Visual check NOT RUN.

Build-only alpha.59 iteration keeps versionCode 59 and permanent certificate; same-version update via package installer depends on installer policy. No data migration. New version number reserved for public release.
