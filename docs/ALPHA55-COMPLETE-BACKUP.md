# Alpha 55 — ZIP v3: complete lab archive backup

Parent: Alpha 54 fc2290a. Its Android/Rust build and Android 35 emulator jobs passed before this patch (run 45389440940). New candidate CI required.

- BK-01: ZIP v3 includes existing history.sqlite, preferences.json and new bounded labs.json (50 Network, 30 Battery). Includes digest in manifest and confirmed identity. v1/v2 remain readable and preserve target lab archives. No new permission or v12 schema migration.
- BK-02: v3 validates entry set, size, SHA-256, typed records, counts, uniqueness and redacted network grouping. Existing lab data prevents v3 restore before main DB commit; no automatic overwrite. v1/v2 preserve labs. Explicit existing settings replacement still disclosed. Checksums detect corruption, not malicious authenticity.
- BK-03: persist lab JSON in bounded recovery journal before main transaction, restore only after matching application_id commits, idempotent exact-row replay after interruption. Save/delete of both labs serialized with backup and blocked during pending recovery. Main DB and two labs are not a single SQLite transaction; recovery is the consistency mechanism. No full DB/setting lock outside current scope.
- BK-04: preview counts and v1/v2/v3 disclosure; excludes pending global undo and its owner. Raw resolved/gateway IPs not exported; explicit host is retained. Videos, screenshots, signing keys, and transient last auto-session report excluded.
- BK-05: upgrade existing emulator round-trip/legacy/tamper/recovery tests for v3 and new populated-lab, identity, and interrupted replay cases. Actual candidate CI pending. No release/version bump.

Test additions to existing two-case FullBackupInstrumentedTest: real lab rows, both counts, duplicate semantic payload rejection with recomputed digest, changed lab identity rejection, populated-lab refusal when main DB empty, successful round trip, blocked edits while journal pending, partial replay of one archive then idempotent recovery, old ZIP v1/v2 preservation. The emulator-only test deletes only its disposable database fixtures. No physical-device deletion. Tests written, exact CI not yet observed.
