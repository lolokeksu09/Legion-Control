# Legion Control alpha.27 — Backend MVVM refactor (stage 2)

## Scope and evidence

This is an **incremental, functional backend migration** from the alpha.26 Navigation shell to lifecycle-aware ViewModels/Repositories. It does **not** assert that all pre-existing laboratory Activities have already been migrated. The source remains Android 13+ Kotlin, Rust/JNI, local SQLite v12; **no Root, Shizuku or new Android permissions**.

### Existing manual diagnostics (former 946-line MainActivity)

- `DiagnosticsHistoryRepository` is the **sole read/format backend** for recent SQLite sessions, thermal history, recent timed battery tests, sensor attempt quality, recent sensor samples and latest-session statistics. All original units, unknown-value semantics, limits and Russian report labels remain.
- `DiagnosticsHistoryViewModel` runs reads on its own single worker and delivers lifecycle-aware LiveData; independent generation counters reject stale results for the same pane. Results dispatch one by one to the main looper without LiveData `postValue` coalescing reports from different panes.
- `MainActivity` (now AndroidX `ComponentActivity`) observes five display regions and routes read requests **through its original write executor first**. That ordering ensures a queued prior SQLite write completes before a read is enqueued. The ViewModel owns and closes its own SQLite helper. A cleared model refuses delayed operations.
- **Original Rust native `engine.record()/statistics()/resetSession()`, 60-second foreground-only test, sensor capture, SQLite write path, full CSV/ZIP export and sample time/thermal guards are kept unchanged.** The activity is still the controller for those Android lifecycle-sensitive operations in this stage, explicitly for reliability.

### Capture Studio

- `CaptureStudioRepository` owns persisted presets and app-owned MediaStore clip listing/deletion; confirmation is required in the Activity and the backend **rechecks ownership in the current list** before deleting the selected URI. `CapturePresetRules` strictly validates presets before writing preferences.
- `CaptureStudioViewModel` owns the clip I/O executor, settings and loading/ready/error states across Activity recreation. The Activity renders an **inline clip list**, so a video dialog is not inadvertently re-opened after rotation.
- **System MediaProjection consent remains only in Activity, freshly requested each recording**. The existing `CaptureRecordingService` and atomic `IS_PENDING` MP4 lifecycle are byte-for-byte unchanged. No microphone or new file access.
- Launch/view/share intents remain explicitly user-initiated, and confirm-delete continues to be the only path to destructive action.

### Unchanged architectural foundation

- `NavigationHostActivity` with five Navigation Component destinations / Game Hub; `HubViewModel`/`HubRepository`; all original tools, app package `dev.lolokeksu.legioncontrol`, private signing key, Rust JNI, foreground services, Android manifest permission allowlist and SQLite v12 schema unchanged.
- No hidden FPS/CPU/GPU switches, no BootReceiver, no privileged OEM controls.

## Verification plan

```bash
python3 scripts/check_alpha27_mvvm_backend.py
python3 scripts/check_alpha26_mvvm_navigation.py
gradle --no-daemon testDebugUnitTest
bash scripts/build-native.sh
gradle --no-daemon assembleDebug lintDebug
```

GitHub Actions additionally runs all prior SQLite migration, Kotlin, Rust, signing and anti-privilege checks, and releases with the same pinned permanent certificate. Test source: `BackendRefactorRulesTest` rejects invalid quality presets and preserves reader pane identity.

**Manual acceptance still required on Lenovo Y70/Motorola ROM:** install signed alpha.27 over signed alpha.26; verify historical SQLite sessions and Rust sample appear; sensor/thermal/timed history separate panes; save a new battery sample then immediately refresh history; stop a timed test, check save order; Capture Studio presets persist across restart; record/stop/preview MP4; rotate during clip list; confirm deletion does not affect other clips; reopen from home/back; test denial of MediaProjection; check foreground notification. CI never proves physical OEM behavior.

## Deferred full-migration tasks

This release covers the two highest-risk read/media boundaries, not every legacy module. Remaining:
1. Move the 200+ line Rust `MainActivity.sample()` orchestration into a carefully tested lifecycle-aware native controller without changing SQL write ordering or manual-only sampling.
2. Move the Game Session/Smart Profiles/Application Center, Haptic/Audio, Connectivity and Experiment labs to dedicated ViewModels and repositories, retaining their existing Android API permission flows.
3. Consolidate legacy UI into navigation destinations and unify screen state after backend migration. Existing screens stay reachable; no premature deletion of any user feature.

Plan and requirement IDs: [ALPHA27-MVVM-BACKEND-REFACTOR-PLAN.md](ALPHA27-MVVM-BACKEND-REFACTOR-PLAN.md).
