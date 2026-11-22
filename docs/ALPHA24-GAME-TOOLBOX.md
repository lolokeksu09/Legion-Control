# Legion Control alpha.24 — Game Toolbox

A functional, explicit **floating game toolbox** for Android 13+. It does not benchmark games and does not attempt privileged OEM gaming controls. All supported apps are eligible, not just Wild Rift.

## What's new

- **Movable overlay bubble (LC)** over other apps: drag to position, tap to expand and collapse. Saves position, configurable 44/56/68 dp diameter and 60/80/100% opacity. It is a small, non-focusable `TYPE_APPLICATION_OVERLAY`, not a fullscreen transparent touch layer or input capture.
- **Real music volume controls** (one device volume step per tap) via Android `AudioManager.STREAM_MUSIC`.
- **Manual Focus controls:** turn Do Not Disturb ON or OFF using Android `NotificationManager`, only if Notification Policy access is granted; set brightness to 50% / 80% via the existing safe `SmartSystemControls` and optionally restore the original brightness/mode later. Permissions are obtained in the regular Android Settings interface.
- **Launcher integration:** choose any launcher-visible application, store its package and label locally, start the bubble and launch the selected game from one tap, or reopen selected game from the bubble. Does not request `QUERY_ALL_PACKAGES`. Missing/invisible packages are not assumed uninstalled.
- **Explicit lifetime:** manually launch from Game Toolbox Activity; foreground service with a persistent notification and STOP action, a visible STOP control in the panel, and `START_NOT_STICKY` policy. No boot auto-start, covert tracking, Accessibility, Root or Shizuku.
- **Session timer:** simply counts elapsed time since the toolbox was started. It is **not** game play time, match length, precise OS screen usage or frame tracking.

## Getting started

1. Open Legion Control → **GAME TOOLBOX**.
2. Optionally select a launcher-visible game/app.
3. Tap **РАЗРЕШИТЬ ПОВЕРХ ДРУГИХ ОКОН**, enable the overlay permission in Android Settings, and return.
4. Tap **ВКЛЮЧИТЬ ПЛАВАЮЩУЮ ПАНЕЛЬ**, or **ЗАПУСТИТЬ ПАНЕЛЬ И ИГРУ** if you selected an app.
5. Drag LC to a comfortable location; tap to expand. Every action runs only when you tap its button.
6. To change DND, grant Notification Policy access via Game Toolbox settings. To change brightness, grant Modify System Settings access. Re-enter the game and manually tap the action.
7. Tap **СТОП** in the panel or **Остановить** in the foreground notification. To undo brightness, open the controller and use **ВОССТАНОВИТЬ ИСХОДНУЮ ЯРКОСТЬ**.

## Permission, privacy and compatibility limitations

New special-access manifest permission: `android.permission.SYSTEM_ALERT_WINDOW`; **not** automatically granted by installing. Reuses existing `FOREGROUND_SERVICE_SPECIAL_USE`, `MODIFY_AUDIO_SETTINGS`, `ACCESS_NOTIFICATION_POLICY`, `WRITE_SETTINGS`, `POST_NOTIFICATIONS`. Android can restrict floating windows for security/particular apps, and OEM builds may reject special-use foreground services or launching apps from a background overlay. Permission denial results in no toolbox launch. System Settings remains the source of truth; neither the overlay nor game launcher bypasses OEM restrictions.

The notification might not be shown in the notification drawer on Android 13+ if POST_NOTIFICATIONS is denied; the running foreground service is still visible in Android's system FGS manager when supported. The toolbar always offers STOP. No location, microphone, screen recording, background scans, online traffic or unauthorized app controls have been added. Public Android APIs do not enable changing another application's FPS, GPU clock, refresh rate, touch reporting rate, game graphics, Bypass Charging or device-wide power policy here.

DND and volume modify **global** settings, not individual-game settings; closing the toolbar does not revert them. DND OFF selects the system's ALL interruption filter rather than restoring an earlier custom priority policy. The existing brightness restore action restores the captured system brightness/mode if the Android write succeeds, but it is not automatic and may be rejected by a ROM.

## Data, upgrades and testing

- Same `applicationId=dev.lolokeksu.legioncontrol`, same permanent release signing contract; `versionCode=24`, `versionName=0.1.0-alpha.24`.
- **No SQLite schema change**: previous v12 battery, sensor, session, application library, audio and haptic history remains in the same database. Only a new private preferences file for toolbox settings is added.
- Pure JVM tests: `ToolboxTuningTest`. New strict static contract: `python3 scripts/check_alpha24_game_toolbox.py`. Existing Rust, Android, SQLite and signer gates continue unchanged in both CI workflows.
- Manual device checks outstanding: Android overlay permission accept/deny; bubble drag/collapse and coordinate clamp across rotation; gesture passthrough in a real game; notification STOP with denied/granted notification permission; volume at 0/max; DND access deny/approve and impact on preexisting custom modes; WRITE_SETTINGS and restore; selecting/launching visible game; leaving launcher/locking device; replacing installed permanently signed alpha.23 and retaining SQLite data. **Do not equate a passing CI build with on-device verification.**

[Implementation plan](ALPHA24-GAME-TOOLBOX-PLAN.md).
