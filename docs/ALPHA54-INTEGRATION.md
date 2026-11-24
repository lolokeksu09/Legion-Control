# Alpha 54 — integration candidate

Base: published Alpha 49, 5b0b957819daf43edde84dacf94ef20ceda0f316. Integrates PRs #68–71 at exact heads:
- Alpha 50: 5947c6a78ae2c3663e61850ffb6d60b23ff8b354
- Alpha 51: b666683dadf756738117924d82759d77ec97f755
- Alpha 52: 8d693a409b25ba6d8a71dc847fb99ca1277cf570
- Alpha 53: d67a771c31a389498b510ab74613488bce8d0aa2

INT-01: preserve independent feature changes and all new emulator gates. Shared smoke script is the only overlapping changed file; retain live network, battery, automation ownership and resource tests. No original branches deleted.
INT-02: cross-module source review and fix confirmed lifecycle/rollback/measurement defects without schema or permission expansion. Verify final commit with existing Android Kotlin/Rust CI, source gates, unit tests, emulator, and separately physical hardware.
INT-03: publication remains gated; retain Alpha 49 version/signing until separately approved release preparation. No Beta release or physical-performance claim. New archives are not covered by existing full backup ZIP; explicit CSV export only. Physical network, battery, automation and visual/SAF acceptance outstanding.

Source integration is not a passing build. See exact PR CI for evidence.

## Source audit corrections

- INT-02a: network confirmation before replacing an unsaved test; visibility gate and consistent save/delete callback state even if temporarily hidden; SAF CSV error feedback if payload/view missing. Equivalent battery archive callback handling.
- INT-02b: restore requires Android readback of original value before clearing a field from durable journal; retain brightness-mode ticket if brightness restoration fails. Tests cover accepted, ignored, rejected and unreadable writes through a fake setting. No actual DND or OEM settings restoration claimed.
- INT-02c: watchdog checks arm/overall deadlines independently of pending query; recheck ownership before processing delayed observations. Clock baseline uses simultaneous elapsed/wall start; stop request is not ignored just because overlay start is still queued.
- Remaining: complete visual/device acceptance, archive ZIP integration, measured performance, game event limitations and final release metadata. Existing full backup format intentionally unchanged.
