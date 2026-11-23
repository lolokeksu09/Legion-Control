# Legion Control v0.1.0-alpha.43 — Unified AMOLED frontend

A UI-only release based on the approved Home/Games layout in alpha.42.
Same app package, permanent signing identity, Android permissions, media
recording consent, data schema and Kotlin/Rust behavior.

## Updated interface

- Shared black (#000000) foundation and consistent panel (#12161C),
  outline (#252B33), accent (#86B7FF) and secondary text (#9CA5B0).
- Auxiliary modules use uniform touch-friendly action cards, disabled/focused
  states, dark text-field backgrounds and accent checkbox states.
- Section headings follow the blue hierarchy used on the Home screen.
- Capture Studio, Kotlin/Rust Diagnostics and Gamepad Live have a canonical
  LEGION CONTROL header and the five-tab dock; tapping a tab returns to the
  NavigationHostActivity.
- Preserved Home/Games icons and their existing card UI, Game Toolbox
  compact controls, Game Detail, Capture/Replay and manual system actions.
- Display Patterns stays intentionally fullscreen to keep display testing valid.

## Validation

- GitHub source contract + Kotlin/JVM, Android native Rust, APK and lint checks.
- Android 15 emulator navigation and screenshot smoke for the main tabs and
  existing modules; no simulated benchmark or fabricated FPS.
- The real Legion Y70 / Motorola firmware requires a manual check for font
  scale, game overlay permissions, screen rotation, touch targets and
  interruptions during recording.

This is a preview release, not a declaration of complete physical-device QA.
