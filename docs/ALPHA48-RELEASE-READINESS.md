# Alpha 48 — consolidated release candidate readiness

Base: PR #65 head daa1e968465ffd717eb5b573695031982050acb9. Candidate includes the original #59 and batches #61–65. Main is still published Alpha 47 at 5004071afba60342a4a489b946f7d8e2d3240286; comparison before this document: ahead 113 commits, behind 0, 111 changed files. A consolidated draft PR targets main; no merge or release yet. Do not merge overlapping stacked PRs separately.

## Implemented (A48-13/14, release gates)
- Common fail-fast source runner includes all 52 prior Android CI checks in their original order plus SBOM unit test (53 total), and existing Workflow Standard source check. Signed release now runs the same suite; previously 13 of the Android checks were absent in release workflow. No original source gate removed. Existing JNI, Gradle, signing and exact-main emulator publication gates retained.
- Both CI pipelines resolve debug/release runtime classpaths with an isolated Gradle init script. No new build plugin or Maven dependency. Reject unresolved dependencies; emit resolved module coordinates. Python produces separate CycloneDX 1.5 inventories with Git checkout SHA, app version, resolved Maven components and Cargo.lock packages. Local legion-core is not mislabeled as a published registry package. Artifacts include both inventories; future signed release attaches releaseRuntimeClasspath.cdx.json alongside APK. Scope: resolved runtime dependencies, not full APK composition, build plugins, license attestation, dependency graph or verified vulnerability-free status.
- Source audit confirms three declared runtime AndroidX dependencies, three test dependencies and Cargo.lock containing only local legion-core (no third-party crates). Full transitive runtime inventory requires CI. Dependency vulnerability scan, Gradle artifact verification/locking and SBOM schema validation remain open.
- Corrected smoke harness's fixed success count: #64 run 45335227035 build PASS, emulator job 138247320009 failed even though LiveMonitorInstrumentedTest reported OK (3 tests); old harness accepted only OK (1 test). New expected count is exactly three monitor tests and exactly two backup tests, with all other existing gates unchanged. No skip/no weakening. Full new emulator run required; #65 run 45335727753 still in progress at lookup.
- Updated consolidated release notes (ZIP v2/v1 compatibility, audit fixes, limitations).

## Evidence
- Source inventory compared old 52 vs new 53: no missing gate, no duplicates, original order preserved, one added test.
- Local command `python check_runtime_sbom.py -v`: 3/3 PASS on byte-identical Git blobs e9fb43a439b16912239c6e18d2cb02b6cbfbac6f (generator) and 42bffd4032511109cc53fd6f5514ed911821a7e4 (tests). Tests cover local native identity, variant separation/deduplication, empty and unsupported input. This is synthetic inventory mapping, not actual Gradle resolution.
- Repeated synthetic SQLite experiment on host SQLite 3.46.1, 60,000 rows, 100 packages, 100 reads: existing index p50/p95 0.218/0.644 ms; candidate package+time+ID index 0.030/0.198 ms; same 21 rows, temp sort removed. Run-to-run timings vary; no Android latency or energy claims, no schema migration.
- New exact candidate CI, real SBOM artifact generation and permanent APK signing NOT RUN at documentation time. Physical Legion Y70, TalkBack/font scaling, provider interruption, measured startup latency and database index adoption remain open. No new permissions, SQLite schema, signing key, package ID or version changes; no publication.

## Delivery gates
1. Inspect exact consolidated PR build and emulator CI; fix any failures. Inspect actual resolved SBOM artifacts.
2. Review outstanding audit findings and physical-device acceptance separately. No exceptions are self-authorized.
3. Only after required gates and authorized merge, allow main workflow to sign with existing certificate and publish Alpha 48; confirm exact artifact and source SHA. Do not manually replace any release asset.
