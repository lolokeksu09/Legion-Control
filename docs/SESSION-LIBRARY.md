# Session Library

Adds a read-only browser for recorded Game Session history. Entry points: History, Tools, feature search and game detail.

- Filter by recorded package, all/7/30/90-day periods, and completion state (all, USER_STOP, other ended records, no end record).
- Stable keyset paging: 20 rows/page, next/previous, ordered by started_at_ms and session_id. Parameterized SQLite values; a refresh fixes the time cutoff. The history is live, not an immutable database snapshot; records that finish while browsing may change status.
- Existing package chooser exposes up to 40 recent recorded packages; all-games browsing can reach other packages. Up to 500 pages per filter; refine the filter to browse additional history.
- Selecting an ended record shows existing battery/thermal summary, sample count, events and last ten available event descriptions. A record without completion cannot be exported.
- Export an explicit selected record via Android SAF ZIP: session.csv, samples.csv, events.csv, README.txt. No other session or global settings are included. Read at most 10,000 samples and 2,000 events; reaching either cap is flagged as possibly incomplete. Null values are empty fields and text is CSV-escaped. This ZIP is a diagnostic archive, not a full importable backup.
- No permissions, new database schema, recorder start, deletes or system-setting writes.

## Regression evidence and release gate

Three preceding revisions passed Kotlin/Rust build and Android instrumentation (monitor, preflight, compare); the common UI smoke failed at back_until("Готов к запуску") after successfully pinning a Live Monitor baseline. The failure hierarchy captured Android Launcher rather than the app (run 45321500240, artifact 14888459140). The old helper retried Back when a scrolled/loading Home title was not visible. The new test uses one Back, stable host header and selected tab, then scrolls to the title. This hypothesis requires exact-head emulator verification. Do not label the complete UI smoke PASS until it runs successfully.

New JVM tests cover filter bindings, page boundary, archive structure and CSV protection. New emulator-only test seeds controlled local fixtures, checks timestamp-tie pagination, package/status isolation, refusal of unfinished export and archive contents. Synthetic fixtures verify data handling, not actual gaming performance. No real device or signed release is claimed.
