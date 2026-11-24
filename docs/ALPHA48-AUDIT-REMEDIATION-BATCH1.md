# Alpha 48 — audit remediation, batch 1

Base: e537c8a0ab41cf0618ea6c1277258c9743eabdee (PR #59).
Audit: PR #60 / FULL-AUDIT-ALPHA48.md. This branch is stacked on feat/session-library; do not merge independently into main or publish an APK until the integrated exact revision passes Android CI.

| Finding | Implemented | Evidence / remaining gate |
|---|---|---|
| A48-02 | Capture Studio explains before SAF export that per-game Preflight requirements, MP4 and PNG are excluded. No ZIP format or restore semantics changed. | Disclosure only; full versioned preset backup and backward-compatible restore remain OPEN. |
| A48-03 | Compare/Library keep a single selected export snapshot in their ViewModel. Duplicate picker requests are refused. A callback consumes the snapshot once and validates current selection identity and busy state. Cancel/launch failure permits retry; process death refuses export rather than switching records. | PendingDocument pure Kotlin smoke PASS (exact blob 0ff7c31e9b62dd045db7072ed79c8dd675f22420); JUnit tests added, Gradle and real SAF rotation/I/O NOT RUN. Live Monitor export still open. |
| A48-04 | Standalone Preflight selects Games; Compare/Library select History; remaining existing routes unchanged. | Source review; emulator selected-state/back navigation NOT RUN. |
| A48-07 | Preflight ViewModel schedules expiry using elapsed time; invalidates launch freshness and updates status after 60 seconds. Clears callbacks on state update/stop/clear; old reports remain shareable. Launch button disabled when stale; existing click-time revalidation retained. | Source review; actual expiry, background/resume, critical/warning flow NOT RUN. |
| A48-12 | Connectivity history captures host at enqueue. Delete carries ID in result and clears selection only on main thread when request generation is current and ID matches. | Source review; delayed delete + host change/reselection instrumentation NOT RUN. |

## Verification

- Local kotlinc harness compiled and executed the exact PendingDocument source (git hash-object matched GitHub blob); PASS for original object identity, duplicate refusal, single consumption, cancel/retry and fresh owner. This is not an Android rotation test or a Gradle/JUnit run.
- Candidate e537c8a0 build PASS; emulator in progress at last lookup (45326107579). That evidence does not cover this changed branch.
- Local Gradle/Android execution unavailable; GitHub clone fails DNS and Android runtime is not connected. Full branch CI and emulator acceptance required; no signed release, merge, schema change, permission change or certificate change requested or performed.
- Remaining: A48-01, full A48-02, remaining A48-03/04/05/06/08/09/10/11/12/13/14. No physical device claims.
