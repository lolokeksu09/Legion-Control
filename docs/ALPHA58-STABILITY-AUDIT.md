# Alpha 58 — focused stability and release-readiness audit

Base: Alpha 57 corrected head ee79845. Prior CI 45444672713: Kotlin/Rust build PASS; emulator stopped at clock-tick output handoff, before benchmark. Corrected PR #84 uses private cache file from Android sysconf; new CI 45449139598 in progress at audit start.

## Findings and changes
| ID | Evidence / boundary | Change and test |
|---|---|---|
| STAB-01 | ZIP replay clears imported preferences, including the same store used for global launch-profile undo. Previously an apply could race after restore preflight, or replay could clear a pending undo ticket. | Non-blocking reentrant process-wide maintenance gate on restore/replay and game profile save/apply/undo. Block new profile writes while durable journal exists; preserve any pre-existing undo ticket and require manual return before replay. Manual undo can resolve a pending journal when no restore is executing. JVM gate concurrency test and actual interrupted ZIP fixture exercise preservation. |
| STAB-02 | Network archive action accepted a callback while screen hidden; delete dialogs captured a record independently of current selection. | Visible-screen guard and exact selected record ID recheck at confirmation in both diagnostic archives. No automatic deletion or data migration. |
| STAB-03 | Benchmark custom instrumentation status omitted in actual runner output. | Fixed in parent PR #84, raw instrument output retained, private calibration file. No fabricated measurements. |

## Remaining acceptance
- Exact new build, source checks and Android emulator smoke must pass. Actual benchmark samples still unknown. No before/after speedup claim.
- Physical Legion Y70: ARM64 launch; camera/MediaProjection/audio; Android/OEM permission flows; usage-event delays and DND/brightness readback; actual network and battery probes; signed update and ZIP/SAF on physical device. No physical connection available in this session.
- Full visual review of new screens at real device font scale. Existing tests are layout and interaction checks, not a human visual audit.
- Existing ZIP recovery uses journaled replay across separate DBs, not a cross-database transaction; ZIP excludes media and volatile reports. Concurrent unrelated settings edits during replay are not globally serialized; this patch protects launch profiles/undo specifically.
- Existing version metadata and signing release tag remain alpha.49; Beta 1 requires explicit version/release authorization and exact main CI. No release published by this PR. Release pipeline must avoid replacing existing tag.
- This is a targeted source audit of changed modules, supported by prior emulator checks; not exhaustive verification of every physical capability.
