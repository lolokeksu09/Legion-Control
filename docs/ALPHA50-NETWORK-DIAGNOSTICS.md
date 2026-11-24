# Alpha 50 development — Network Diagnostics 2.0

Base: published Alpha 49 / 5b0b957. First vertical slice, inside existing Connectivity Observatory. No new permission, database migration, dependency, background service, version bump or release publication.

- NET-01: explicit 20-sample live mode, domain/IPv4 input, fixed TCP 443 via captured Android Network, stop on screen exit. DNS may outlive cancellation; token rejects stale callbacks. Host sees connection IP; no payload, no TLS/authentication claim.
- NET-02: Android LinkProperties IPv4 default Wi-Fi gateway, bounded platform reachability via interface; unavailable for VPN/non-Wi-Fi/unsupported route. Not guaranteed ICMP; no reply is inconclusive.
- NET-03: two elapsed-time charts, gap on failure/network switch, TCP variation from adjacent successful samples on same network/endpoint; no single-sample zero jitter. Failure percentage includes DNS/TCP errors among attempted probes, not packet loss. Route handles and addresses stay in memory only.
- NET-04: sampled network transitions, not a continuous callback or definitive cause attribution. Show local and external results separately; no inferred game latency or automated fixes.
- NET-05: existing DNS/TCP/TLS history/export remains separate. Live mode is RAM-only in this increment; no saved live history or historical graph.

Acceptance: unit cases for host rejection, missing samples, failure denominator, route/endpoint changes and jitter gaps; exact PR Android Kotlin/Rust CI, visual smoke (existing general smoke is not proof of new live mode). Physical Wi-Fi, cellular, VPN, failed DNS, gateway non-response and screen-stop checks pending. No release claim until version gates are updated and signed release authorized.

## Increment 2 — local archive and reproducible comparisons

- NET-06: explicit 10/20/40-probe plans (1/1/3-second pause after a probe); DNS may extend elapsed time. Cancellation token also checked inside the probe after DNS and before connecting.
- NET-07: explicit Save on complete/partial run; private SQLite archive `legion_live_network.db` separate from existing v12 database, 50 records maximum, no automatic deletion. Duplicate ID does not overwrite. Route handles, resolved IPs and gateway addresses are redacted to run-local group IDs; user-entered target is stored intentionally. Unsaved results are not guaranteed to survive screen recreation/process death.
- NET-08: reopen saved result with charts, median/p95/max, failures and gateway stats. A/B compares distinct complete runs with identical target and plan, not causal diagnosis. CSV exports a frozen selected report through SAF; no automatic sharing. Explicit confirmation before single-record deletion.
- NET-09: archive independent of existing legacy CSV/ZIP backup; no schema migration or new dependency/permission. Archive is NOT included in full-backup ZIP in this increment. Physical network and SAF/UI verification remain pending.
- Verification additions: pure JVM redaction/comparison/CSV tests and emulator-only real archive persistence/deduplication/delete test, explicitly gated in smoke script. Exact candidate CI required.

## Increment 3 — IP family controls and evidence-based diagnostics

- NET-10: Auto / IPv4 only / IPv6 only modes, one TCP candidate per probe, no transparent fallback in explicit modes. NO_ADDRESS is separate from DNS/TCP errors. Host input remains DNS name or IPv4 literal; IPv6 uses a DNS hostname. DNS resolver timing is omitted for IPv4 literals, includes failed resolutions, and may reflect cache.
- NET-11: Android validated-network flag, actual attempted family, DNS timing and separate evidence counts. Toggle TCP/DNS chart within existing panel. Local gateway and external outcomes are paired by observed probe; no automatic attribution to provider/router/game.
- NET-12: A/B deltas for matched completed tests, same plan/target and either same IP mode or explicit IPv4 vs IPv6. Auto cannot be compared with a forced mode. At least 3 measurements per side for median/p95 differences; percentage points for error-rate change. No extrapolated latency or packet loss.
- NET-13: JSON payload v2 inside same isolated SQLite schema; read v1 with unknown DNS/family/validation, never fabricate values. CSV extends columns; identifiers stay redacted. New unit tests and emulator archive compatibility/capacity gate. Exact new revision CI pending; physical network/SAF/visual acceptance still outstanding.
