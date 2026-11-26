# Capture Center consolidation — 2026-11-26

Base: `69fca63`. Signed CI failed at check_alpha24_game_toolbox.py: legacy direct DND/settings assertions after centralizing permissions. The guard now follows Toolbox → SystemControlPanel → CapabilityAccessBackend; no permission check removed.

- CAP-01: one foreground status reflecting the process-wide CaptureCoordinator plus service state; starts disabled when busy, pause/resume combined, active commands only. One-second presentation refresh while resumed, stopped on pause.
- CAP-02: one user-triggered media library refresh for owned MP4 and PNG with independent loading, error, empty and pagination states. Existing editing, confirmation, sharing and ownership checks retained.
- CAP-03: Toolbox and its overlay route to the existing Capture Studio; no additional Activity, service, permission, storage schema or capture engine.
- CAP-04: existing Android consent, selected-game audio, original-file preservation and ZIP backup contract unchanged.

Verification: updated source guard added to canonical CI; Android compilation and signed artifact must be checked on the exact new revision. No device/emulator acceptance performed in this session. Version remains alpha.59; this is a follow-up build, not a new published version/tag.

## Follow-up CI repairs

- `d4a94d3`: retain stable feature IDs and align search with the shared media library.
- Restore the shared Notice presentation separately for MP4 and PNG loading, error, empty and ready states. The existing unified-dialog guard remains unchanged; the capture-center guard now verifies both independent state paths.
