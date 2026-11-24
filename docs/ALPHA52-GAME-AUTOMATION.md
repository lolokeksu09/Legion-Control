# Alpha 52 development — Game Automation

Base published Alpha 49 / 5b0b957. Independent from pending Alpha 50/51 branches. Explicit one-shot opt-in in existing Game Toolbox (no new permission/service/boot receiver or hidden restart).

AUTO-01: choose launcher application and existing saved profile; disclose exact global changes and approve one session before launching game. Requires existing overlay/Usage Access/visible notification access. Arm for up to 90 seconds, poll UsageEvents ~2s only while armed, cap at two hours. No permanent other-app names or raw usage events. Fresh target resume only; ambiguous or stale events cannot trigger writes. Android event delivery can be delayed/omitted; this is not a reliable foreground detector for all OEM/multiwindow cases.

AUTO-02: existing preflight and journal used for auto apply; approved profile compared again inside lock, owner UUID atomically stored with undo ticket before writes. Only owner-matching auto restore; manual restore still works, user-changed values protected by existing compare-before-restore. If process killed, durable ticket remains for explicit manual restore; no guarantee of onDestroy. No privileged control.

AUTO-03: stop on observed target exit/another resume, screen lock, access loss, large observation/clock gap, thermal guard, max time, explicit notification stop or panel stop. No re-apply after exit. Store only latest local completion report: selected package, estimated observed elapsed, max sampled battery temp/thermal status and rollback result; not a match tracker or existing session database. Stop on 45°C or Android SEVERE+, visible notification update. No new background recorder or automatic actual game session history integration.

AUTO-04: existing service special-use description updated; no permission changes. API 33–36 notification, usage, OEM behavior and actual rollback need physical acceptance. Added pure event-policy tests; ownership/settings runtime verification pending. Exact CI required. No release/version change.
