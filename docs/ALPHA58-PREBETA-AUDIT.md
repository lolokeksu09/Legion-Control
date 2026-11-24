# Alpha 58 — pre-Beta audit and release gates

Candidate parent f3132f3 (Alpha 57), stacked on #76. Main and release unchanged.

## Findings
- P1 PERF-01: run 45400905966, job 138468921612 failed parser fixture because the CPU regex accepted a colon prefix of a secondary process name. Require whitespace/end after exact package delimiter; retain original failing regression and add wrong-PID/zero cases.
- P1 DATA-01 (source audit, not a reproduced race): ZIP restore checked pending profile undo only once before long I/O, while manual/automatic profile writes use a separate lock. A new undo record could be cleared by later preference replay. Reserve a short-lock, thread-owned restore lease across import/replay, reject profile save/apply while reserved or journal pending, refuse acquisition if undo exists. Nested recovery on same thread allowed; release in finally. Instrumentation extends existing ownership test; no actual global settings writes by new assertions.
- P2 evidence: Alpha 54 exact CI 45389440940 PASS both jobs; Alpha 55 corrected build PASS at inspection, emulator pending; Alpha 56 build pending; Alpha 57 source gate FAIL as above. New exact candidate CI required.
- Source review: app declares allowBackup=false, internal capture/automation services non-exported; launcher and system-protected Quick Settings tile remain existing entry points. No permission or signing changes. This is scoped source inspection, not a security certification.
- No version bump, merge to main, production release, or user data deletion. Existing alpha.49 release workflow pins its existing tag; do NOT merge this stack to main as a release without a separately authorized version/release metadata change.

## Beta 1 readiness
| Gate | Required evidence | Current |
|---|---|---|
| L1/L2 | exact source, Kotlin/Rust tests, lint, APK validation | NOT RUN on new candidate |
| L3 | Android 35 real instrumentation including ZIP v3 and ownership, chart selection, smoke | NOT RUN on new candidate |
| Profiling | actual process JSON and raw measurements, comparable baseline if claiming savings | NOT RUN |
| UX | real screenshots at normal and enlarged fonts; verify actions and readability | NOT RUN |
| Device | Lenovo / target Android APIs: network, battery, automation apply/restore and interrupted recovery | NOT RUN |
| Release | versionCode >49, beta notes, pinned permanent cert, exact main CI, explicit authorization | BLOCKED |
| L5 | install published signed APK over existing release, launch and preserve user data | NOT RUN |

No exception authorized. Emulator evidence never substitutes for physical settings restoration or performance. Before publication, verify v3 restore on disposable fixtures and do not clear actual user data. No hidden changes to existing archive scope. 
