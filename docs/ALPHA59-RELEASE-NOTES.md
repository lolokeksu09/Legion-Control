# Legion Control 0.1.0-alpha.59

Stability and performance candidate following the published alpha.49. Consolidates the audit and PR #87/#88 changes: guarded startup/ZIP recovery, verified manual setting writes and owned rollback, common permission states, Game Hub library and navigation, game profile confirmation and local session analytics, shared replay live/export budgets for H.264 and AAC, and hidden Toolbox timer suspension.

Application ID, pinned permanent signing certificate, SQLite v12, and arm64-v8a/x86_64 JNI remain unchanged. No root/Shizuku or hidden OEM API added. Replay memory ceilings apply to retained compressed data, not process PSS. Physical-device validation is still required. Install over the compatible release-signed previous version without uninstalling.
