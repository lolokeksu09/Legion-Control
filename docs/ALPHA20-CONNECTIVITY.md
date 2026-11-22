# Legion Control v0.1.0-alpha.20 — Connectivity Observatory

Universal network troubleshooting for Android 13+ / 4PDA. No Root, Shizuku, Accessibility, location, VPN tunnel or new Android permissions.

## New capabilities

- **Three selectable public diagnostics targets, HTTPS port 443:** `one.one.one.one` (Cloudflare), `dns.google` (Google), `dns.quad9.net` (Quad9).
- **IPv4 and IPv6 are tested separately:** DNS resolution, one TCP connection candidate per available address family, and an HTTPS TLS handshake to the actual hostname. Trust validation and HTTPS hostname verification use Android's normal system store, with SNI enabled. No `trustAll`, no certificate bypass.
- **Single test:** one DNS lookup and up to two TCP/TLS attempts; timing is measured on a worker, not the Android UI thread.
- **Stability test:** user-started, up to six cycles spaced around 12 seconds apart. It only operates while the screen is active in Connectivity Observatory. Press Stop or leave the screen to request cancellation. On certain Android builds a platform DNS lookup can outlive the cancellation request; the next cycles are not run and any partial records remain.
- **Recording:** private SQLite v9 with complete/failed/cancelled/interrupted states, crash recovery, samples with DNS/TCP/TLS timings, family, Android network transport, `NET_CAPABILITY_VALIDATED` and bounded error codes. IP addresses, SSID, MAC address, traffic payloads and DNS answers are **never stored**.
- **Analytics:** DNS measured once per cycle (not double-counted for IPv4/IPv6), TCP- and TLS-handshake median/p95 in milliseconds, success/failure counts, unavailable IPv6 indicated separately, limitations for fewer than three cycles and incomplete tests.
- **History and comparison:** browse local runs, view individual cycle results, compare the two latest runs of **the same public host**, review last seven days (up to the last 100 runs), share a chosen report, delete one run.
- **Export:** CSV for all stored diagnostic tests, streamed from SQLite to a user-selected document via Android Storage Access Framework. Nothing uploaded automatically.

## How to test

1. Open Legion Control → **CONNECTIVITY OBSERVATORY** (also available from Smart Profiles).
2. Read the disclosure: public endpoints see a connection from your public IP.
3. Pick Cloudflare, Google DNS or Quad9. Use **Один замер** or **Тест стабильности**.
4. Leave the lab open while running; repeat on Wi-Fi and then mobile data, or with/without VPN.
5. Compare the same target's last two runs; compare `network_type` and DNS/TCP/TLS timings, not a "gaming latency score".
6. Open History to inspect error codes. Optionally export the entire history as CSV or share just one report. Deletion applies only to the chosen network run.

## Interpretation: what the numbers mean

| Field | Meaning | NOT |
|---|---|---|
| DNS ms | Time Android took to resolve the selected hostname (may use caches) | Router ping, CDN performance rating |
| TCP ms | Time for TCP connection establishment to the chosen public server | ICMP RTT, game-server ping |
| TLS ms | Time spent on a certificate/hostname-verified TLS handshake *after TCP* | Overall page-load time |
| TLS success | A trusted, named TLS session was established | Internet speed, zero packet loss |
| IPv6 unavailable | DNS returned no IPv6 candidate for this run | Proof that IPv6 is broken |
| Network validated | Android system marks network as validated | Verification of access to game servers |
| median/p95 | Descriptive timing of a small local sample | Statistical reliability or a performance guarantee |

No actual packet-loss or throughput meter, no DNSSEC status claim, no capture of third-party network traffic and no automatic background monitoring. Comparison is descriptive, not proof of a causal improvement. VPN, network selection, DNS caching, radio idle states and CDN routing can change measured results.

## Privacy and 4PDA compatibility

- Existing package `dev.lolokeksu.legioncontrol`, versionCode `20`, Android 13+, ARM64/x86_64 Rust JNI. The permanent release signing identity must remain unchanged.
- SQLite upgrade v8→v9 is **additive**; game sessions, profiles, annotations, A/B experiments and samples are not altered. Only two new tables are added. On launch, previously unfinished network tests are marked `INTERRUPTED`.
- Only explicit button presses make the outbound connections. The public diagnostic providers may see your source public IP; it is not forwarded to any Legion Control server and is not saved by the app.
- There are no new permissions. The older Game Session and System Controls permissions remain governed by existing alpha.17–19 contracts.
- A failed or unavailable IPv6 address is shown as an independent state, not concealed as a TCP or TLS failure.
- User-owned CSV export can contain timestamps and network types. Review before sharing online.

## Release test checklist

- CI: real SQLite v8→v9 data preservation, FK/negative tests and legacy regressions; pure JVM median/p95, missing family, DNS/TCP/TLS errors; debug Android assemble + lint; Rust native ABIs.
- Release: unsigned release must be rejected, temporary signer must fail the pinned fingerprint test; permanent signing workflow must verify the signed APK.
- Physical devices **not verified by CI**: installation over previous signed alpha.19 on Lenovo Legion Y70 with Motorola firmware and preferably a second OEM; repeat Wi-Fi/mobile/VPN, no-internet, IPv6-only/IPv4-only, cancellation on Home, certificate failure, CSV export and history preservation. No compatibility guarantee until tested.

[First-commit plan and verification gates](ALPHA20-CONNECTIVITY-PLAN.md).
