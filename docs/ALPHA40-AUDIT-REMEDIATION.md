# alpha.40 audit remediation — 2026-11-23

Baseline: `0c251f063a6595cab36e37bb37faa306d205b143` (alpha.39). No Root,
Shizuku, hidden OEM APIs, new Android permissions, SQLite schema migration or
automatic collection/deletion of user videos is added in alpha.40.

## Acceptance matrix

| Audit finding | Implementation | Gate / remaining evidence |
| --- | --- | --- |
| LC-01 Version 27 despite alpha.39 | Gradle `versionCode=40`, `0.1.0-alpha.40`, CI artifact naming updated; original Android package and release signer retained | Mandatory CI and pinned certificate; in-place physical install remains unverified |
| LC-02 Physical instant replay not tested | Existing explicit Replay controls and opt-in per-game audio preserved | Hardware Y70 gameplay/audio/thermal test remains external to CI |
| LC-03 Race between recording, replay and screenshot | `CaptureCoordinator` atomically reserves user consent and serializes service session lifecycle | `CaptureCoordinatorTest`, source contract; emulator MediaProjection behavior still requires functional test |
| LC-04 Pending MediaStore orphan cleanup | `CapturePendingRecovery`: user confirmation; only own matching 24h+ unfinished media; no silent data pruning | `PendingCapturePolicyTest`, source guard |
| LC-05 Full backup/restore unavailable | `LegionDataBackup`: SAF ZIP, SQLite v12 consistent snapshot, 256MiB DB cap, SHA256, schema/quick_check, preference export, preview and opt-in restore into empty database | `BackupRestorePolicyTest`, source guard. Restore is intentionally blocked for a populated database |
| LC-06 Replay video/audio drift | Existing bounded AAC capture retained, approximate clock offset clearly disclosed | True sample-accurate sync and measured drift need physical playback testing |
| LC-07 First 100 MP4/PNG files only | `CaptureClipLibrary` and `CaptureScreenshotLibrary` page with 40 items, explicit Next/Previous, ownership check independent of current page | Source contract; MediaStore pagination requires emulator/hardware QA |
| LC-08 Screenshot requested during Replay | Consent reservation now occurs before invoking the Android consent UI | `CaptureCoordinatorTest`, source contract |
| LC-09 Main heading shows alpha.26 | `NavigationHostActivity.versionLabel()` reads installed APK version | Source contract and emulator smoke |
| LC-10 Insets, accessibility | `LegionUiSupport` handles status/navigation/cutout/IME on legacy Views screens; back target 48dp, actual selected-tab state for TalkBack | Static source; manual dark/light, font-size and focus testing still needed |
| LC-11 DNS cancellation appears immediate | UI reports that cancellation is *requested*; stops new cycles, doesn't falsely claim interruption of Android DNS | Existing Connectivity unit/CI; uninterruptible platform DNS cannot be forcibly canceled |
| LC-12 CSV escaping mismatch | `CsvEscaper` centralized RFC4180 cell handling and formula-prefix escaping, including Unicode invisible characters | `CsvEscaperTest` |
| LC-13 Design inconsistencies | Shared insets and nav state introduced without changing the established visual identity, widgets remain native Android Views | Comprehensive visual redesign deliberately not asserted without screenshots |
| LC-14 Media usage unknown | `CaptureStorageOverview` shows owned MP4/PNG item counts and sizes; no deletion without user action | Source guard, device storage QA remains |

## Backup privacy and deliberate limitations

- SAF ZIP includes **SQLite history v12** and selected non-secret app configuration
  SharedPreferences. It does NOT include MP4, PNG, application signing keystores,
  permissions, global Android DND or display settings, external-app data or cloud data.
- Archive may contain game/app names, timestamps and local observations. **Do not
  publish or upload it to GitHub**. Keep it in a private location.
- Import first verifies ZIP entry names, size limits, package/schema, SHA256 and
  SQLite quick_check, then rejects any database with existing application rows.
  It does **not** erase or replace a populated database. It rejects import
  during Game Session recording or unresolved system-profile undo.
- Database insert is transactional. Preference changes are committed separately
  by Android SharedPreferences and are best-effort reverted on failure;
  system-wide atomicity between database and preferences is **not guaranteed**.
  A new device / empty install is the supported restore target.
- Movie/screenshot libraries remain app-owned MediaStore; existing public files
  survive in-place APK updates as Android permits. Creating a backup does not
  promise external video preservation on uninstall.
- Each recording needs **fresh** system MediaProjection consent. No permission
  bypass or stealth/background start is implemented. No automatic deletion.

## Verification gates and release

1. `python3 scripts/check_alpha40_audit_remediation.py` plus all existing
   alpha.11–39 source guards.
2. `gradle --no-daemon testDebugUnitTest` for pure JVM invariants.
3. `bash scripts/build-native.sh`, `gradle --no-daemon assembleDebug lintDebug`.
4. GitHub Actions Android 35 emulator smoke. Smoke checks launch/navigation;
   do not equate them with real gameplay, AAC capture or full SAF backup recovery.
5. After **passing** exact-head CI and authorized merge, signed `main` workflow
   must verify the same PKCS12 certificate and `versionCode=40` before distribution.
6. Real-device acceptance: install over an older **permanently signed** release;
   preserve SQLite history, verify MediaProjection consent/denial, replay and
   internal game audio, rotation, temperature, two successive replays, MP4
   playback and A/V drift, app killed during record, low space, permission
   revocation, backup restore on clean install and screen-reader focus.

Not verified means **NOT RUN**, not success. Never merge or distribute failing
builds or label the build stable before the mandatory checks pass.
