# Game Toolbox idle optimization — follow-up candidate

Parent: verified Beta 1 candidate a02654b / successful CI 45405804990. This change is isolated from the release branch and from workload experiment #79. No version, permission, database, signing, automatic profile or publication changes.

## Found
The previous service eagerly created all panel controls (seven rows of buttons, header, timer and feedback), then hid them. Its timer posted a main-thread callback every second even when only the LC bubble was visible. Hidden TextView text was formatted and assigned repeatedly. These are actual source-level overheads; no measured RAM/CPU saving has been established yet.

## Change
- Initially create only the root and draggable LC handle. Create the panel once, on first expansion. Retain it after collapse so existing interactions/state and repeat opening do not reinflate controls.
- Cache latest feedback even before panel creation, then use it when expanded. Preserve the independent automation controller, its notifications, and manual actions.
- Cancel all timer callbacks on collapse/destroy; only schedule while the panel is visible. On reopening compute elapsed time from the original monotonic service start, align the next callback with the next whole second, skip duplicate text assignment. A queued reposition ignores a later collapse.
- No new persistent background worker, permissions or privileged APIs.

## Acceptance
JVM tests exercise collapsed, reopen, elapsed and exact boundary behavior; source guard verifies actual startup and collapse wiring. Android build and existing emulator regression required for the exact revision. Real overlay expansion/collapse, drag, stop and comparative paired PSS/CPU on the same emulator and build variant still require runtime evidence. In the collapsed-before-first-expansion case the panel views are not instantiated and the display timer has zero recurring callbacks; do not confuse this structural change with a measured reduction in total CPU, RAM, FPS or power. The old successful Beta 1 candidate remains unchanged.
