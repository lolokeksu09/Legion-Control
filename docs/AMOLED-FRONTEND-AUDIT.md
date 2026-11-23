# Legion Control — unified AMOLED frontend audit

Source audit of the alpha.42 baseline and alpha.43 frontend branch against the approved Home / Games screenshots
(23 November 2026). The screenshots are a reference, not proof that every module
was visually inspected on a phone.

## Findings
- **High:** Smart Profiles, Application Center, Game Session, Session Signals,
  Session Baselines, Session Incidents, Experiment Lab, Compatibility Lab,
  Hardware Lab, Gaming Lab and other auxiliary fragments use different action
  surfaces, spacing and typography. Common styling was previously selective
  and excluded modules with custom button backgrounds.
- **High:** Capture Studio, Kotlin/Rust diagnostics and Gamepad Live were
  standalone Activity pages without the common header and five-tab shell.
- **Medium:** EditText and CheckBox contrast/focus states varied across modules.
- **Intentional exception:** Display Patterns must remain unobstructed for
  actual visual testing. The on-game floating Toolbox window also has its
  own layout requirements and is not styled like a settings page.

## Frontend corrections
1. Centralized shared AMOLED tokens: black background, #12161C panels,
   #252B33 borders, #86B7FF accent, #9CA5B0 secondary text.
2. All auxiliary NavHost fragments receive shared tactile button cards,
   disabled/focus states, form fields and checkbox styling. The approved
   Home, Games, Game Detail and Toolbox surfaces are preserved.
3. Standalone Capture Studio, Rust diagnostics and Gamepad Live use the same
   common header and five-icon dock, without altering their Activity ownership.
   Back and tab navigation resolve to the canonical NavigationHostActivity.
4. Retain the exact existing package, permission model, SQLite schema,
   MediaProjection consent and game profile actions.

## Verification matrix
- Source assertions: theme integration, navigation shell, existing screens,
  no extra Android privileges.
- Kotlin/JVM and Rust Android build/lint: required in GitHub Actions.
- Emulator: Home, Games, Tools, History and Settings navigation and screenshots;
  Capture Studio, Game Toolbox, Game Session, Smart Profiles, diagnostics and
  session tools retain smoke checks.
- Legion Y70 with Motorola firmware: **not run** until the signed build is
  installed on the device; real typography, gestures, overflow and activity
  switching require device-side verification.
