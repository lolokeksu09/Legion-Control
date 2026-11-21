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

## Alpha.8 — export legacy and diagnostic measurements together

- The **existing** explicit `ACTION_CREATE_DOCUMENT` flow now streams a 20-column CSV, containing all former 13 columns unchanged plus seven fields: `thermal_status`, `thermal_headroom`, `charge_counter_micro_ah`, `current_now_micro_a`, `current_average_micro_a`, `ram_available_bytes`, and `diagnostic_timestamp_utc`.
- SQLite uses a `LEFT JOIN` on the composite immutable `session_id` + `elapsed_ms` keys. Every v1 legacy row remains present even if no v2 diagnostic exists; the seven new values stay **blank**, not synthetic zero. The unique key guarantees one-to-zero-or-one diagnostic and prevents duplicated CSV readings.
- All string cells use existing always-quoted RFC4180-style escaping, BOM, UTF-8, CRLF and UTC ISO-8601 timestamps. Missing Android values stay blank. Export is an explicit user action and uses the Android system file picker. No network access and no new permissions.
- This does not change SQLite schema v2, old data, Rust ABI or the existing share report. `CsvReport.begin/append` remain backward-compatible for callers; `writeCsv` uses the extended methods. Avoid interpreting raw charge counter values as measured battery health or real charge-adapter wattage.
- `scripts/check_extended_diagnostics_csv.py` executes the actual export SELECT on SQLite, checking v1-only records, matching v2 diagnostics, no duplicated rows and exact columns; `ExtendedCsvReportTest` validates formatting, escaping, blank data and wrong-row rejection. Physical Y70 SAF-export test still required.


## Alpha.10 — additive sensor history in SQLite v3

New table sensor_samples stores only accepted SensorEvent readings: unique (capture_id, kind), wall-clock and elapsed realtime timestamps, accuracy and 1 or 3 sensor axes. Captures do not require a Rust battery session. v1→v3 creates diagnostic v2 and sensor v3 tables; v2→v3 creates only sensor v3 table. All original sessions, samples and diagnostic_samples records remain intact. Unsupported migrations fail rather than wiping anything. The check_sensor_sqlite_migration.py script executes SQL against real SQLite from both starting schemas and checks old-row preservation, unique captures and invalid constraints.

No automatic pruning; app-private history is deleted by uninstall, not by Rust reset. Update alpha.9 with the same permanent release signer; do not uninstall to swap in a debug APK. The existing 20-column battery CSV remains unchanged and does not include sensor readings. New UI action «Последние показания датчиков» reads persisted values after app reopen; new captures compare raw values with the last saved reading of that sensor kind. Physical tests must verify changes in light, proximity and movement, listener cleanup and no battery-history regression.


## Alpha.11 — SQLite v4 diagnostic attempts and thermal timeline

Additive CREATE TABLE sensor_probe_attempts plus index; v1→v4 creates v2 and v3 tables first, v2→v4 creates v3 first, and v3→v4 creates only the attempt table. The SQLiteOpenHelper upgrade transaction is atomic; no DROP or DELETE. A capture saves at most one successful sensor sample per sensor, plus one attempt row per completed probe with sensor type, status, Android-reported mode, event/rejection counts, optional reason and latency. This preserves unsuccessful attempts without inventing sensor measurements. Previous alpha.10 samples persist; old capture IDs do not acquire synthetic attempt records. Cancellation on Activity stop unregisters sensors and doesn't fabricate a completed attempt.

Thermal history is read from the existing battery samples LEFT JOIN diagnostic_samples, with nullable status and headroom. Legacy records without diagnostic data remain visible with unknown fields, not implicit zeros. New UI reads data on user request. All battery CSV columns remain unchanged; sensor probe attempts are kept in the private database. Uninstall still deletes app data (allowBackup=false); use same-signer in-place update. Physical Lenovo Y70 acceptance remains required after CI.
