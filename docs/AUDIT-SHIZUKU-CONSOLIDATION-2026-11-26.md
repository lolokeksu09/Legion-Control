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
