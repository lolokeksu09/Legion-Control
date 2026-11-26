# Legion Control 0.1.0-alpha.59

Stability and performance candidate following the published alpha.49. Consolidates the audit and PR #87/#88 changes: guarded startup/ZIP recovery, verified manual setting writes and owned rollback, common permission states, Game Hub library and navigation, game profile confirmation and local session analytics, shared replay live/export budgets for H.264 and AAC, and hidden Toolbox timer suspension.

Application ID, pinned permanent signing certificate, SQLite v12, and arm64-v8a/x86_64 JNI remain unchanged. No root/Shizuku or hidden OEM API added. Replay memory ceilings apply to retained compressed data, not process PSS. Physical-device validation is still required. Install over the compatible release-signed previous version without uninstalling.


Additional alpha.59 integration: optional Shizuku API 13.1.5, shell UID and permission diagnostics, consolidated 19-module tool registry, simpler Settings and compact summary/permission rows. Daily CI skips the full emulator suite per owner request; build and permanent certificate checks remain. No privileged write operations or root functionality added in this phase.


Build-only refinement: fixed read-only Shizuku background policy report, shared Toolbox selection with ZIP gate, game detail grouping and actual app icon, pagination navigation. Version remains alpha.59, no public release; exact SHA identifies build.
