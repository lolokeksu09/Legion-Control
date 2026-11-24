# Alpha 48 — audit remediation, batch 4

Base: 780fb859a9764b6ea4d574cdbc7823fada4e2e62 (PR #63). Exact base workflow 45331692279 completed/success (build and Android 35 emulator); PR #62 workflow 45331061295 also completed/success. The new revision requires its own CI.

## A48-05 — stable search identity
- Migrated the remaining 53 catalog controls across 16 modules; 79/79 explicit bindings across 21 modules. Removed label-based fallback; duplicate/missing visible identities fail closed. The finder reveals the control only, never executes an action. Existing IDs, action callbacks and module routes retained.
- Re-read all 21 changed/base module sources and checked each registry ID was present exactly once in its binding, with the correct anchor and binding helper: 46 + 33 verified, zero mismatches. Updated the existing CI source contract to enforce this.
- Added instrumentation to the existing LiveMonitorInstrumentedTest for changed labels, duplicate/hidden identities, disabled target and no click. Full runtime testing of all routes and dynamic availability remains open.

## A48-10 — compact shared controls
- Standard shared buttons: 48 dp minimum rather than 56; vertical padding 9 dp rather than 13; shared bottom spacing 6 dp rather than 10. Wrap content retained for multiline labels.
- Shared action tiles: 48 dp minimum rather than 56, smaller internal padding, single accessibility description (decorative duplicate label descendants excluded). Section labels marked as accessibility headings.
- Both bottom navigation implementations: 12 sp labels instead of 10, no hard two-line truncation, 48 dp minimum and smaller outer padding. Full section names used in standalone accessibility descriptions. NavHost title receives remaining width instead of intrinsic overflow.
- Added shared button instrumentation covering minimum size, wrap-content layout and mixed-case text. No assertion of visual compliance or screenshot review on this new revision. TalkBack, font scales 100/150/200%, 320 dp, landscape, contrast and physical device measurements remain NOT RUN.

## Verification and constraints
Source binding inventory passed. Updated CI source contract and added Android instrumentation; exact new build/test/emulator results not yet available. No changes to database schema, permissions, signing certificate, version, package identifier or published APK. Do not independently merge this stacked PR into main. SQLite profiling, startup recovery performance, and supply-chain/version consolidation remain open.
