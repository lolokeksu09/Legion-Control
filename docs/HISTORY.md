# Legion Control: persistent diagnostic history (v0.1.0-alpha.4)

## Data model and operational boundary

**Two independent layers:** Rust retains up to 64 samples in process RAM for native trend/min-max-mean statistics and elapsed duration. Kotlin stores approved *successful manual measurements* in private Android SQLite database `legion-control-history.db` (`SQLiteOpenHelper`, schema v1). Kotlin does not fake Rust results; native statistics are never silently reconstructed from a previous boot.

- `sessions`: UUID session ID, first sample's wall-clock timestamp and nullable explicit end timestamp.
- `samples`: session ID, UTC epoch milliseconds, Android elapsed realtime milliseconds, battery charge %, battery temperature in deci-C, voltage/current when available, status/source of charging, power saver state and display mode metadata.
- Each pair `session_id + elapsed_ms` is unique; transactional writes ignore exact-timestamp repetitions, and invalid native readings never enter SQLite.
- An explicit Rust reset **closes** the current SQLite session but does not erase persisted rows. Fresh measurements start a fresh session. A process restart also starts a new session; the old session remains available but does not falsely claim an explicit end.
- Database has **no auto-cleanup**; future migration must preserve user data. Existing `allowBackup=false` means Android uninstall deletes this app-private history. An in-place APK update using the *same permanent signing key* preserves it.

## User flows

1. Open application: review previously saved sessions and sample counts. **No startup or background measurement happens**.
2. Tap **Обновить измерение**: read Android battery and power public APIs; Rust validates, stores in its native RAM window and returns native statistics. The accepted snapshot is persisted in SQLite with an unambiguous session ID. A database failure is reported on screen as a failure; native results alone are not counted as persistent history.
3. Tap **Обновить список сеансов**: view the latest 12 sessions, with time, number of samples and elapsed wall-clock difference from first to last stored sample. The number displayed is the number of sessions shown; all stored rows remain in SQLite.
4. Tap **Завершить сеанс Rust**: explicit confirmation clears only Rust's current 64-sample in-memory window, flags the SQLite session as ended, and clears the last shareable snapshot. Database records remain untouched.
5. Tap **Сохранить историю в CSV**: Android system `ACTION_CREATE_DOCUMENT` prompts the user to choose the filename and destination; the app writes a BOM-marked UTF-8 CSV directly to the user-granted document URI. Export is streaming and doesn't request storage permission or write to a public path without a picker.
6. Tap **Проверить доступность датчиков**: query `SensorManager.getDefaultSensor` for accelerometer, gyroscope, light, proximity and ambient-temperature types. This is **discoverability only**, not sensor sample collection or a CPU/GPU thermal reading.

## Export fields and constraints

CSV columns: `session_id`, `timestamp_utc` (ISO-8601), `elapsed_ms`, `battery_percent`, `battery_temperature_deci_c`, `charging_status`, `charging_source`, `voltage_mv`, `current_ua`, `power_saving`, `display_refresh_hz`, `display_width_px`, `display_height_px`.

Every cell is quoted; quotes are doubled, rows end CRLF, unavailable optional values are empty rather than fake zero. CSV creation is explicitly user-initiated and the chosen output location is outside the private database. Take care with the generated export: it reveals device usage times, device readings and app session identifiers.

## Test evidence requirements

- `python3 scripts/check_persistent_history.py` validates privacy boundaries, SQLite transaction and no-destructive migration contract, export and sensor paths.
- `gradle --no-daemon testDebugUnitTest` exercises pure CSV and correct Russian `измерение/измерения/измерений` grammar.
- `bash scripts/build-native.sh` runs native Rust tests and compiles ARM64/x86_64 JNI; debug and release CI compile Kotlin and package binaries; release must verify pinned permanent certificate.

**Physical Lenovo Legion Y70 acceptance (not established by CI):** install same-signer signed alpha.4 over signed alpha.3 (no uninstall), tap «Обновить измерение» twice, close and reopen app, confirm records remain, export CSV to Downloads and inspect headers/2 rows, inspect available sensors, start a new session after confirming Rust reset, ensure previous SQLite history remains. Reboot device and repeat the persistence check. **Do not uninstall** an existing signed release just to test a debug APK.

## Alpha.7 — lossless SQLite v1→v2 diagnostic extension

- The existing `sessions` and `samples` tables and all their rows are retained; `onUpgrade(1, 2)` only creates `diagnostic_samples` plus an index within SQLiteOpenHelper's upgrade transaction. Any unsupported migration still throws, rather than wiping the database.
- Each valid diagnostic references its **existing** legacy sample through `FOREIGN KEY(session_id, elapsed_ms)` and `PRIMARY KEY(session_id, elapsed_ms)`. Unavailable Android values are SQL NULL, and duplicate accepted manual requests are not counted twice.
- `LocalHistoryStore.recordDiagnostic` runs on the application's diagnostic executor, never on the UI thread. The existing CSV export remains **v1-compatible and unchanged**; the new diagnostic fields are retained in the private database and displayed in the current shareable report, not yet added to the CSV columns. No silent backfill of unmeasured values in pre-migration records.
- The last five measurements **within the current process's session** are used to calculate temperature range/change and charge-counter rate over observed time only; no monitoring service, nominal capacity guess or battery health scoring.
- The actual v1 and v2 DDL is executed against SQLite by `scripts/check_diagnostic_sqlite_migration.py`, verifying preservation, referential integrity, unique keys and out-of-range thermal constraints. Android on-device upgrade and persistence still require an actual device smoke test.
