# alpha.38 — per-game Capture Studio recording profiles and manual JSON backup

## User functionality

1. Choose a game in **Game Toolbox** (using its existing installed-app selection).
2. Open **Capture Studio → Профили записи по играм → СОХРАНИТЬ ПРОФИЛЬ ВЫБРАННОЙ ИГРЫ**.
3. Set resolution (long edge 1280/1920), requested 30/60 FPS, video bit rate 4/8/12 Mbps, auto-stop 1/5/15 minutes, and the separate opt-in playback-audio flag. With a saved game profile selected, changes affect **that game only**; other games and global values are untouched.
4. Selecting a different game in Game Toolbox switches to that game's saved preset, or the global settings if none exists. Opening the studio again refreshes this selection. The selected source game must be currently launchable.
5. The recording request takes one immutable snapshot of the selected game's target, settings and audio flag **before** asking Android for fresh MediaProjection consent. Even if game selection changes while the system consent dialog is open, the recording service receives the original requested snapshot.
6. The game-profile reset action has a confirmation. It restores global fallback **without deleting** video files, screenshots or other game profiles.
7. **Export** and **import** game recording profiles with Android's Storage Access Framework, only by explicit user action. Import requires confirmation and atomically replaces the independent *game-profile store* without touching global capture settings. Malformed and duplicate entries are rejected before any write.

## Architecture and safety

- New pure `GameCaptureProfile` / `GameCaptureProfilePolicy` with strict validation reuses `CapturePresetRules`.
- `GameCaptureProfiles` uses a **separate** `SharedPreferences` file `legion-game-capture-profiles`; maximum 64 package-keyed records. This deliberately avoids changing SQLite v12 or global recording options.
- Manual UTF-8 JSON: `{"schema":1,"profiles":[{"package":"com.example.game","edge":1280,"fps":30,"mbit":4,"minutes":5,"audio":false}]}`. The backup is bounded to 128 KiB; duplicates, unsupported schema, invalid packages, invalid FPS/bitrate/minutes and malformed Boolean values are rejected. A complete in-memory parse happens before the single `SharedPreferences.Editor.commit()` transaction. No write occurs on partial validation failure.
- `CaptureStudioRepository` maps the existing selected Game Toolbox app to the active game profile (if present) or old global preferences (otherwise). Existing global preferences remain readable and use legacy `CaptureConfig.fromOptions` fallback normalization.
- `CaptureStudioViewModel` manages profile state, captures consent settings, and queues import/export I/O off the UI thread. No projection tokens are saved in preferences or backup files.
- `CaptureStudioActivity` adds only minimal controls for saving/removing per-game overrides and using Android's SAF document chooser for JSON backup/restore.
- Recording continues to require each new `MediaProjection` system consent. Playback capture still requires explicit `RECORD_AUDIO` grant and can only target the chosen game's UID. This is *not* microphone capture.
- No Root, Shizuku, new permissions, broad gallery access, app scans, hidden OEM APIs, server traffic or diagnostic modules.

## Limitations and verification

- The saved profile changes **recording settings**, not the game's FPS, graphical options or Android system performance. Requested 60 FPS is subject to actual codec and device support.
- Profiles are keyed by package name. A restored profile becomes active only when that game is installed, visible and selected in Game Toolbox.
- If Android recreates the UI process during a consent dialog and the ViewModel snapshot is lost, the request fails safe and must be started again; it never starts a different game profile silently.
- The user must have sufficient file permissions through SAF to export/import. Import intentionally replaces the entire saved *game-profile set*, after confirmation. Export/import contain no private keystore.
- CI checks JVM pure policy tests, source gate, full Android build/lint, Rust JNI, existing permission contracts and emulator smoke. Physical Lenovo Legion Y70 acceptance must separately check switching games across different saved quality/audio options, persistence after restart, SAF roundtrip, denied system permission, game selection change while consent is visible, and captured MP4 settings/sound. CI alone is not that hardware test.
- Signed release is produced by the protected existing `main` workflow after merge using the pinned permanent keystore and checking certificate SHA-256. A debug APK cannot replace the signed installation. VersionCode/versionName remain pinned at 27 by the existing codebase, to be coordinated separately.
