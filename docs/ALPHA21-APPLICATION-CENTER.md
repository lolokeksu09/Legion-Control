# Legion Control v0.1.0-alpha.21 — Application Control Center

Universal, offline-first management of **your own app/game library** (Android 13+, no Root/Shizuku). Uses Android's public PackageManager and stock Settings screens; not a privileged application manager or game booster.

## New features

- **Launcher app catalog:** choose and search apps visible through Android's `MAIN / LAUNCHER` query. You can enter a package name manually if the app chooser cannot display it. A hidden or inaccessible package is not proof the application was deleted.
- **My Library:** manually track up to 150 packages, save a custom alias, category (up to 40 characters), personal note (up to 1000 characters), mark favorites, view favorites first or filter only favorites.
- **App metadata:** on-demand version name + version code, Android first install/last update timestamps, system package flag, enabled flag, battery optimization exemption as visible to `PowerManager`, and declared requested permission names with best-effort granted flags.
- **Manual Update Watch:** save an observed package fingerprint; on subsequent manual checks detect changes of version/update timestamp and changes in Android's package visibility. The app does **not** run an invisible background scanner. Leaving the screen stops a batch scan after the current package.
- **Local event history:** first observed, version changed, became visible or became unavailable, with deduplication when the observed fingerprint is identical. "Unavailable" is NOT "uninstalled".
- **Optional Usage Access:** if the user grants Android's existing Usage Stats special access, view the selected package's aggregate foreground duration and most recent observed usage for 24 hours or 7 days. Without access, the feature clearly reports that no data is available.
- **System management shortcuts:** open official app info, per-app notification settings, Android battery optimization settings and launchable app intents. The user changes settings in Android, not by Legion Control silently modifying them.
- **Integration:** open Smart Profiles or Experiment Lab with the currently selected app package.
- **CSV export:** manually export the tracked library and events using Android Storage Access Framework, with formula-injection escaping. Notes are local and may contain personal information.

## How to use

1. Open Legion Control → **APPLICATION CENTER**.
2. Choose an installed launcher app or search by label/package name; optionally enter a valid package directly.
3. Fill alias/category/note and choose **В избранное**. Press **СОХРАНИТЬ В БИБЛИОТЕКУ**.
4. Press **Проверить версию и состояние выбранного** to save an initial version snapshot and subsequent updates. **Проверить изменения всех** scans only the tracked list **when you tap**.
5. Use **Моя библиотека / избранное**, **Только избранные приложения**, **Последние события обновлений** for navigation and audit.
6. Use **Карточка приложения** to inspect observable version, requested-permission flags and available battery-exemption status.
7. Optionally grant Usage Access via Android Settings. There is no requirement to do this to use library, favorites, package cards or system shortcuts.
8. Export CSV only after pressing the export button; review private notes before sharing. Deleting a tracked package removes only **Legion Control's** notes/events for that package, not the installed app or game sessions.

## Boundaries, interpretation and privacy

| Displayed data | Source and limits |
|---|---|
| Visible/unknown package | Android PackageManager with launcher-app visibility restrictions; unknown/invisible does **not** establish uninstall |
| Version/update | PackageInfo observed during an explicit manual scan; may be missing on restricted OEM builds |
| Requested permission flags | PackageInfo granted flags at scan time; may be incomplete and are not permission-management controls |
| Battery optimization exemption | `PowerManager.isIgnoringBatteryOptimizations()` as allowed by the device; other OEM policies are not measured |
| 24h/7d foreground duration | `UsageStatsManager.queryAndAggregateUsageStats()`, only with optional user-granted special access; not game-match duration |
| Open notification/app settings | Public `Settings` intent; Android controls actual permission/change |
| Application events | Inferred from local differences between manual observations; **not** a guaranteed real-time Android installation/update feed |

**Never claimed:** actual game FPS, CPU/GPU clocks/temperature of other apps, privileged app killing, hidden permissions changes, bypass charging, CPU scheduler controls, global background monitoring, or hidden package enumeration.

## Upgrade and verification

- App ID remains `dev.lolokeksu.legioncontrol`; versionCode **21**, Android 13+, ARM64 and x86_64 native Rust libraries.
- Additive SQLite **v9→v10** adds `app_library` and `app_audit_events`. No existing game sessions, notes, A/B experiments, connectivity, battery history or profiles are intentionally modified. Untracking an app deletes only its own new-library audit history, via FK cascade.
- No **new** Android permissions (optional usage access already used by previous releases); no `QUERY_ALL_PACKAGES`.
- Permanent signing cert must be verified by the main release workflow to install over alpha.20.
- Hardware tests remain **NOT RUN** until actually performed on Lenovo Legion Y70 (Motorola firmware) or other OEM. Suggested manual smoke checks: install over signed alpha.20, verify old game/network history, discover apps, favoriting and categories, scan/version update events, official Settings links, Usage Access denial/grant, CSV export, screen exit while scanning, no app uninstallation.

[Implementation plan and release evidence](ALPHA21-APPLICATION-CENTER-PLAN.md).
