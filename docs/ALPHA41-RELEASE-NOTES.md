# Legion Control v0.1.0-alpha.41

Release candidate after `alpha.40`, based on the permanent signer and the same
`dev.lolokeksu.legioncontrol` package. No Root, Shizuku, stealth recording,
new Android permissions, SQLite migration, or unrelated design replacement.

## Improved

- AAC input presentation times follow **actual PCM frame counts** instead of
  individually sampled `System.nanoTime()` on each nonblocking read.
- When supported, `AudioRecord.getTimestamp(TIMEBASE_MONOTONIC)` anchors the
  stream to a measured frame capture timestamp; a timestamp sanity check keeps
  malformed/stale samples from breaking monotonicity.
- Nonblocking reads returning zero bytes no longer submit artificial empty
  encoder input buffers with synthetic media timestamps.
- For monotonic MediaProjection H264 surface timestamps, a delayed codec
  dequeue is no longer incorrectly converted into a video/audio clock offset.
  A fallback remains for devices whose video PTS is stream-relative.
- Unit tests cover a 120-second PCM timeline, read jitter, malformed capture
  timestamps, zero-byte reads, and encoder-dequeue latency.
- Existing SQLite backup/restore instrumented emulator test remains in CI,
  followed by the Android 15 navigation/session smoke.
- Expanded emulator screenshots include Capture Studio, Instant Replay and Game Toolbox.
  Screenshot-derived fixes: literal \\n escape sequences in Capture Studio now render
  as actual line breaks; standalone media/toolbox screens use black system bars.
- Default buttons in auxiliary Navigation modules use shared AMOLED contrast and
  disabled states. Existing bespoke Audio/Haptic/Connectivity button designs are
  preserved. No change to navigation or the established icon set.

## Important limits

These improvements reduce timestamp jitter and long-term audio drift *in the
encoder timeline*. Android game capture policies, device-specific MediaProjection
clock behavior and audible A/V sync require tests on the actual Legion Y70
with Motorola firmware. No claim of hardware-measured frame-accurate sync.

The SQLite archive is local and unencrypted. Restoring it is permitted only
into a clean empty application database and after explicit confirmation.
Existing videos are never deleted automatically.

## Acceptance checks

- Kotlin JVM tests including new Replay PCM/video clock cases
- Rust native build; Android debug & permanent release builds; fixed
  certificate fingerprint match; x86_64 Android 15 emulator smoke
- Install signed alpha.41 over alpha.40 without uninstalling
- Physical device: save 30/60/120-second replay twice, listen to game AAC,
  inspect elapsed audio/video position, monitor thermal load and game FPS
