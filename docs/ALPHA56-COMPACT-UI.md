# Alpha 56 — compact diagnostics UI

Base: Alpha 55 e691f3e (workflow 45396312713 success). Stacked PR; no main merge/release.

## UX changes
- Network: 2-column duration/family and archive actions, start/stop share one slot, one visible 156dp chart selectable directly (TCP, DNS, Wi-Fi gateway), all measurements and warnings retained.
- Battery: time/start/stop share a row with only active action visible, four metrics selected directly, two-column archive controls, unchanged report and safety stops.
- Game Automation: two compact action rows with explicit Usage Access and notification state, unchanged consent and rollback. Fix failure message being overwritten by success.
- Shared row uses existing AMOLED colors and native buttons; >=48dp touch heights, natural wrap, stacks below 300dp or font scale >1.25. Existing hub icons, navigation, data schemas and permissions untouched.
- Source comparison, not rendered visual audit. Instrumentation exercises responsive rows, click preservation, >=48dp height, panel measurement and single visible chart. Physical device, before/after screenshots and actual rendered height savings remain unverified.
