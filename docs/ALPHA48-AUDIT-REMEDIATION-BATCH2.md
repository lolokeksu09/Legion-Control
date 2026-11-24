# Alpha 48 — audit remediation, batch 2

Base: ef894828d99d99eabb74814ed0c060781f6e0a25 (PR #61), previous exact CI run 45327989087 completed/success (build + Android 35 emulator). Original candidate PR #59 run 45326107579 also completed/success. These results do not certify this new revision.

## Implemented

- A48-02: full local backup ZIP now exports format 2 with five preference stores including legion-preflight-v1. Format 1 archives and pending recovery journals remain accepted using their exact four-store schema; they never clear existing Preflight requirements. Format 2 requires the additional store, at most 256 canonical package-specific presets, valid package and codec, and exact typed values. Existing size/hash/SQLite/empty-target rules remain. Journal carries the corresponding version and remains replayable after SQLite commit. Older releases cannot import v2; retain old v1 backups. No SQLite migration or data deletion on real devices.
- A48-06: Settings renders immediately in Loading, Ready and Failed; optional counts are replaced by descriptive text when no snapshot is available. Recovery warning and access to Capture Studio, permissions and retry remain accessible without HubSnapshot.
- A48-05 (partial): all nine Capture Studio search actions explicitly bind stable feature IDs. Resolver prefers a unique bound target, rejects ambiguous legacy text instead of choosing the first match, announces unavailable disabled controls and never executes any action. Other 70 IDs still use the guarded text fallback; complete migration and dynamic re-render testing remain open.

## Verification plan and limits

- Expanded existing FullBackupInstrumentedTest: v2 multi-package and long valid package roundtrip, tampered hash, properly hashed invalid preset, v2 interruption after SQLite commit, v1 journal replay preserving target presets and existing populated-target guard. The destructive fixture is emulator-only.
- Updated source guards for journal call and explicit Capture bindings; not a substitute for runtime.
- Review exact changed commit CI, including all existing Kotlin/Rust and Android 35 smoke/instrumentation. New tests have not yet been executed at documentation time. Settings failure injection, TalkBack, all feature anchors and physical Lenovo Y70 remain NOT RUN.
- No new permissions, package ID, SQLite schema, signing certificate, versionCode or published release changed. Do not independently merge this stacked branch into main. Follow PR #59 -> #61 -> this PR after mandatory gates. Audit PR #60 remains separate documentation.

## Next
Live Monitor export lifecycle (remaining A48-03), other stable IDs (A48-05), metrics/units (A48-08), profiling (A48-09), accessibility (A48-10), physical acceptance and recovery (A48-11/12), supply chain and version consolidation (A48-13/14).
