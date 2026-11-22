# Legion Control alpha.20 — Connectivity Observatory

Status: IMPLEMENTED + MERGED + SIGNED; automated CI verified. Physical Android/OEM execution remains NOT RUN. Plan was created before feature code. Baseline `main` commit `4fad9708b56d1012d99b6b1e1a1a0a936a1962c0`, alpha.19. Universal Android 13+ and 4PDA; no Root/Shizuku, no new permissions, same app ID and pinned release signing.

## Goal
Add a reproducible, opt-in network troubleshooting lab distinct from the alpha.18 one-off Cloudflare check: **DNS + separate IPv4/IPv6 TCP connect + verified HTTPS TLS handshake**, sampling history, controlled 60-second foreground stability run, per-run quality analysis and side-by-side comparison, local CSV export. Do not call connect-time "game ping", "packet loss", "Wi-Fi signal", "network jitter" or speed.

## Grounded design
- Only built-in public diagnostic hosts on port 443: `one.one.one.one` (Cloudflare), `dns.google` (Google), `dns.quad9.net` (Quad9). Display external contact and only connect on explicit user action. No arbitrary host/port, LAN scan, traceroute or background interception.
- A probe resolves addresses, tries at most one IPv4 and IPv6 address per cycle, records DNS resolution in milliseconds, TCP connection to each candidate, and **certificate/hostname-verified TLS handshake** with SNI. Do not bypass TLS trust or hostname validation. IPv6 missing = unavailable, not failure.
- A repeated stability run is initiated with a button while the **activity remains visible**, approximately 60 seconds / up to 6 cycles (DNS may extend duration). Stop/cancel on leaving the activity; no new service, wakelock, BOOT receiver, periodic job or monitoring user traffic. DNS platform resolver may not honor a strict timeout; note this limitation.
- Record transport using `NetworkCapabilities`, not SSID or precise location. Android `NET_CAPABILITY_VALIDATED` is OS assessment and not in-game latency. No device MAC, IPs, network names, packet payloads or user traffic captured.
- Report sample counts/validity, DNS/TCP/TLS medians, successful vs failed connection attempts, individual IPv4/IPv6 availability, Android network transport and warnings. Unavailable/unknown != zero; small-N notes. "TCP connect variance" is not game jitter.
- Every export is user-confirmed via `ACTION_CREATE_DOCUMENT`. No app telemetry backend.

## Ordered implementation
### Phase 0 — plan before code
- [x] Create branch `feat/alpha20-connectivity-observatory`.
- [x] Commit this plan as its first commit.
- [x] Verify existing LocalHistoryStore v8, NetworkDiagnostics, SmartNetworkLab, MainActivity, CI gating scripts and release signing.

### Phase 1 — backend and persistence first
Files: `ConnectivitySchema.kt`, `ConnectivityRepository.kt`, `ConnectivityProbe.kt`, `LocalHistoryStore.kt`.
- [x] Add SQLite v9: `connectivity_runs` and `connectivity_samples` with FK, indexes, bounds and explicit COMPLETE/CANCELLED/FAILED/INTERRUPTED terminal state. Preserve 1→8 migrations without any drop.
- [x] Crash recovery: mark old RUNNING rows INTERRUPTED on next start, preserve samples.
- [x] User-started public-API probe: whitelisted hosts, DNS, IPv4/IPv6 TCP, TLS handshake with default trust + HTTPS hostname validation and SNI, per-attempt error class and elapsed time, network type/validated metadata.
- [x] Transaction-safe writes, deterministic order and length caps, rolling compare view; server/IP metadata not persisted.
- Acceptance: executable SQLite v8→v9 preservation/constraints/FK test plus negative tests.

### Phase 2 — pure analytics and verification
Files: `ConnectivityAnalytics.kt`, `ConnectivityAnalyticsTest.kt`.
- [x] Summarise DNS / TCP / TLS medians and p95, attempts, failures, families absent, cancelled/interrupted vs completed; compare last two runs of a selected provider, make no claims about other apps/server ping.
- [x] Unit tests: no data, DNS failure, family missing, mixed success, small sample, percentile boundaries, cancellation, IPv4/IPv6 behavior.
- [x] Add `scripts/check_alpha20_connectivity.py` with real SQLite migration and manifest/contract checks. Retain all older alpha.4–19 scripts, update strict version and button counts without disabling safety checks.

### Phase 3 — UI and local export
Files: `ConnectivityLabActivity.kt`, `MainActivity.kt`, `SmartProfilesActivity.kt`, `AndroidManifest.xml`.
- [x] AMOLED-visible selectable provider; run single snapshot and ~60s six-cycle foreground test with stop; screen visibility cancellation; progress; prevent double-start.
- [x] Show last tests, compare two runs of same provider, anomalies, CSV through SAF with injection-safe cell escaping. No invisible activity or unsupported status claims.
- [x] Record/report package label **not** associated with actual tested game server.

### Phase 4 — production gate
- [x] Version bump 19 → 20, `versionCode=20`, documentation/4PDA privacy/limitations/upgrade guide.
- [x] GitHub CI: Python migration, JVM, Rust Android ABIs, `assembleDebug lintDebug`, unsigned-release rejection, disposable cert rejection.
- [x] Create PR; merge only after green CI, then signed main workflow must verify permanent pinned release cert. Deliver signed APK as direct chat file.
- [ ] Physical phone upgrade alpha.19→alpha.20, offline/IPv4/IPv6, TLS and Activity lifecycle tests remain NOT RUN until user or device actually executes them.

## Risks
- DNS resolution on Android may block longer than requested; network operations run off main thread, bounded cycles and user stop, but cancellation cannot guarantee immediate interrupt of the OS DNS resolver.
- Connection times to public endpoints are not game RTT or packet loss, and may vary with DNS caching, edge affinity, VPN/proxy/CDN, radio power states.
- TLS endpoint identification and trust-store verification are required; never disable to make a site succeed.
- Network probes will expose public connection metadata to those endpoints (IP observed by server), only after consent. No other telemetry or cloud sync.
- Schema upgrades are additive and transactional; no silent data reset, no deletion of existing sessions.
- 4PDA release's actual OEM compatibility is unverified until installed on hardware.

## Evidence log
- Plan-first GitHub commit: `f95065208722ecfd548170e6b0f3353cbcc0f84b` (before all alpha.20 source changes).
- SQLite migration: PASS — actual v8→v9 schema/data-preservation, FK, invalid states and constraint tests via Python CI.
- Build + tests: PASS — PR #23 and main CI, 41 completed steps: Kotlin JUnit, Android assemble/lint, native Rust ABIs, release rejection gates.
- Signed release: PASS — pinned permanent certificate in main workflow, signed APK artifact independently extracted and CRC/SHA-256 checked.
- Physical device: NOT RUN (Lenovo Legion Y70 / Motorola firmware or other OEM).

## Verified release evidence — 2026-11-22

- PR: https://github.com/lolokeksu09/Legion-Control/pull/23, merged to main commit `376ad47667fcea816d3cf9dc1f73ae88a35dd868`.
- Main full CI: https://github.com/lolokeksu09/Legion-Control/actions/runs/45006152767 — **SUCCESS**, 41 steps.
- Permanent signed release CI: https://github.com/lolokeksu09/Legion-Control/actions/runs/45006152824 — **SUCCESS**, no signing fingerprint errors.
- GitHub Actions artifact: `Legion-Control-v0.1.0-alpha.20-permanent-release-apk` (ID `14748241346`).
- APK SHA-256: `495fc5c3354fe88101c008259486733e9cf75d7dbe0a0212af8104e2b0cec3a0`; size `3,456,241 bytes`. ZIP integrity checked; `lib/arm64-v8a/liblegion_core.so` and `lib/x86_64/liblegion_core.so` found.
- User-visible install and runtime verification still pending; CI cannot demonstrate that DNS/TCP/TLS behave correctly on a particular router or OEM device. Strict TTL of the Android resolver is not controlled by app.

**Publication status:** Signed prerelease APK ready for tester distribution. Public 4PDA device compatibility claims are blocked on hardware tests. This is an additive patch; previous data has not been intentionally removed.
