# Legion Control v0.1.0-alpha.42

AMOLED Game Hub consistency update, based on the same permanent signing identity
and Android package as alpha.41. No new permissions, Root/Shizuku,
data migration, service behavior change or silent game modification.

## Game Toolbox UI
- Game Toolbox now opens as a real Navigation Component Fragment inside the
  same shell as Home and Games. The common Legion Control header,
  blue-accent selected bottom tab, explicit back control and black system bars
  are retained in this inner screen.
- Replace uniform full-width buttons with concise cards grouped by
  current status, game launch, game profile, appearance and system access.
  The card surfaces, outlines, typography and spacing match Home/Games.
- Opacity has 60/80/85/100-percent options (85% is the existing default);
  button size has 44/56/68 dp options. Selection is exposed with
  accessibility state and requires explicit taps.
- Status and action feedback are separated, so profile changes can refresh
  the status without overwriting the action outcome.
- Legacy Activity callers are redirected to the same NavHost destination.
- Fix About text to display the installed package version instead of a stale alpha.26 string.

## Preserved behavior
- Opt-in floating panel and optional launch of the selected game.
- Manual game profiles: media volume, brightness, DND, durable restore.
- Explicit Android special-access screens; no hidden privilege changes.
- Screenshot, Replay, diagnostics, local SQLite and signing identity unchanged.

## Validation and limits
- Alpha.42 source/navigation checks and existing permission/security tests.
- Kotlin JVM, Rust, APK signing identity and Android 15 emulator smoke required.
- Physical Legion Y70 / Motorola firmware visual, touch, 30/60/120s Replay,
  thermal and FPS measurements remain device-only checks.
