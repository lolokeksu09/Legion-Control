# Alpha 48 — audit remediation, batch 5

Base: 4612330f96080f7ef07dd7b8b1e742d3c2538296 (PR #64). Its CI run 45335227035 was still in progress at the last lookup; prior PR #63 run 45331692279 passed build and Android 35 emulator. New revision requires its own CI.

## A48-09 / A48-12 — recovery and bounded history

- Recovery journal reads are bounded before allocation (MAX_PREFS * 2 + MAX_META), rejecting excess input after one sentinel byte. Existing AtomicFile, checksums, commit marker, serialized replay, and application startup ordering retained. This reduces memory exposure from a damaged journal, not proof of lower startup latency. Heavy recovery still runs synchronously before preference consumers; moving it asynchronously requires a tested startup gate for Activities and Services.
- Confirmed backup identity now consists of format version, validated database SHA-256 and preferences SHA-256. The dialog forwards this identity to restore; the freshly staged archive must match before its restore transaction. Same-URI content substitution with valid checksums is rejected; no automatic import. Old archive compatibility and empty-database rules retained. Internal overload serves existing emulator fixtures, not production UI.
- Capture maintenance reservation is released even if export staging creation throws.
- Session Library ZIP writes UTF-8 CSV rows directly through a buffered writer into each ZIP entry; it no longer holds a complete CSV string plus its encoded byte array. Header order, escaping and archive names preserved.
- Library detail reads one sentinel row beyond sample/event limits, returns at most 10,000/2,000 respectively, and marks actual truncation instead of falsely marking exact-limit histories.

## SQLite experiment (not a device benchmark)

SQLite 3.46.1, in-memory synthetic fixture: 60,000 sessions, 100 packages, tied timestamps, filtered first page of 21 rows, 100 repeated reads per configuration. Existing schema index idx_game_sessions_started: p50 0.225 ms, p95 0.384 ms, query plan reports a temporary B-tree for the last ORDER BY term. Candidate composite index (package_name, started_at_ms DESC, session_id DESC): p50 0.029 ms, p95 0.052 ms; query plan uses package and time constraints without temp sort. Returned 21 rows identical. This local synthetic experiment neither measures Android disk I/O nor proves production gain. No index or schema migration included: measure write overhead, unfiltered pages, cursor pages, database size and migration compatibility on Android before adoption.

## A48-13 — supply-chain hardening (partial)

All 17 direct third-party action references across android.yml and release-apk.yml use full commit SHAs (eight distinct actions). Source: action-download SHA lines in successful run 45331692279, build job 138234152497 and emulator job 138236227390. Rust explicitly pinned to observed 1.100.0; existing Cargo --locked and Gradle 9.6.0 retained. All three checkouts disable persisted credentials. Release job always removes its temporary signing keystore; protected-main condition, permanent certificate and exact-commit CI gate retained. Existing release signing static gate now checks these settings. No secret values read or changed. Runner image and indirect dependencies are not fully pinned; SBOM, dependency scanning, provenance and further privilege separation remain open.

## Verification

- Local SQLite synthetic experiment PASS (same result and query plan measured as above).
- Source readback of action pins, token settings, Rust version, recovery guard and bounded reader PASS.
- Added/updated tests: same-URI replacement of valid backup rejected before restore, bounded journal oversize, exact event boundary and overflow, byte-exact quoted CSV output. New Android/JVM tests and full branch CI NOT RUN at documentation time.
- Physical Lenovo Y70, startup ANR/latency, real SAF picker/provider, TalkBack and release signing on this revision remain NOT RUN.
- No SQLite schema, Android permissions, package identifier, signing certificate, version or published APK changed. Stacked PR; do not independently merge into main.
