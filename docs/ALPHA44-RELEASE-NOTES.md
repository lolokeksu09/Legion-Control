# Legion Control v0.1.0-alpha.44

Unified AMOLED frontend completion: legacy module entry paths now honor the
approved Home/Games design, rather than rendering disconnected system-gray screens.

## Corrected
- Gaming Lab, Game Session, Smart Profiles, Audio Lab, Haptic Lab, Application
  Center, Hardware Lab, Compatibility Lab, Connectivity Observatory and Experiment
  Lab now forward their historical explicit Activity Intents into the same
  NavigationHostActivity with its blue-accent header, back navigation and five tabs.
- Retained original Activity names for compatibility with old app links,
  shortcuts and explicit Intents, including supported game/package arguments.
- 13 inner module Fragments explicitly apply the shared black/card/input
  design, regardless of their caller. No dependence on the NavHost callback.
- Gaming Lab and Game Session now group controls with compact blue
  section labels; their existing manually triggered actions are unchanged.
- Game Session battery-temperature timeline follows the same accent palette.

## Unchanged
- Game screen and bottom navigation icons; Home/Games/Toolbox approved design.
- Replay/AAC, MP4, game sessions, local history, Rust and SQLite.
- Manual-only permissions and game profiles.
- Display Pattern's full-screen content, which is intentionally exempt.

## Verification
The exact SHA is built and checked by GitHub Actions Kotlin/Rust and the
Android 15 UI smoke, including explicit entry via GamingDiagnosticsActivity
and GameSessionActivity. The signed release is blocked until main CI completes.
Real device UI and gesture checks on Lenovo Legion Y70 remain separate.
