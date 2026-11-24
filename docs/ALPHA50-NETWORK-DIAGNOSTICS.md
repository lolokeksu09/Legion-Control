# Alpha 50 development — Network Diagnostics 2.0

Base: published Alpha 49 / 5b0b957. First vertical slice, inside existing Connectivity Observatory. No new permission, database migration, dependency, background service, version bump or release publication.

- NET-01: explicit 20-sample live mode, domain/IPv4 input, fixed TCP 443 via captured Android Network, stop on screen exit. DNS may outlive cancellation; token rejects stale callbacks. Host sees connection IP; no payload, no TLS/authentication claim.
- NET-02: Android LinkProperties IPv4 default Wi-Fi gateway, bounded platform reachability via interface; unavailable for VPN/non-Wi-Fi/unsupported route. Not guaranteed ICMP; no reply is inconclusive.
- NET-03: two elapsed-time charts, gap on failure/network switch, TCP variation from adjacent successful samples on same network/endpoint; no single-sample zero jitter. Failure percentage includes DNS/TCP errors among attempted probes, not packet loss. Route handles and addresses stay in memory only.
- NET-04: sampled network transitions, not a continuous callback or definitive cause attribution. Show local and external results separately; no inferred game latency or automated fixes.
- NET-05: existing DNS/TCP/TLS history/export remains separate. Live mode is RAM-only in this increment; no saved live history or historical graph.

Acceptance: unit cases for host rejection, missing samples, failure denominator, route/endpoint changes and jitter gaps; exact PR Android Kotlin/Rust CI, visual smoke (existing general smoke is not proof of new live mode). Physical Wi-Fi, cellular, VPN, failed DNS, gateway non-response and screen-stop checks pending. No release claim until version gates are updated and signed release authorized.
