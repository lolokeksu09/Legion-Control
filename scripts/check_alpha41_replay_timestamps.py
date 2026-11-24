#!/usr/bin/env python3
"""alpha.41 Replay correctness guard; tests run on JVM, not real game hardware."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/dev/lolokeksu/legioncontrol"
TEST = ROOT / "app/src/test/java/dev/lolokeksu/legioncontrol"
AUDIO = (SRC / "ReplayAudioCapture.kt").read_text()
VIDEO = (SRC / "ReplayCaptureService.kt").read_text()
CLOCK = (SRC / "ReplayPcmTimeline.kt").read_text()
CLOCK_TEST = (TEST / "ReplayPcmTimelineTest.kt").read_text()
VIDEO_TEST = (TEST / "ReplayVideoClockTest.kt").read_text()
RELEASE = (ROOT / ".github/workflows/release-apk.yml").read_text()
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text()

assert "AudioTimestamp.TIMEBASE_MONOTONIC" in AUDIO
assert "audio.getTimestamp(audioTimestamp" in AUDIO
assert "ReplayPcmTimeline(48_000, 4)" in AUDIO
assert "if (bytes > 0)" in AUDIO and "heldInputIndex = -1" in AUDIO
assert "pcmTimeline.packetStartUs(" in AUDIO
assert "ReplayVideoClock.offsetFromDequeue(" in VIDEO
assert "usesAudioRecordMonotonicCaptureTimestampAndFramePosition" in CLOCK_TEST
assert "delayedReadAndTimestampJitterDoNotCauseCumulativeDrift" in CLOCK_TEST
assert "noPcmMeansNoCodecInputAndNoClockAdvance" in CLOCK_TEST
assert "encoderDelayIsNotConfusedWithTimebaseOffset" in VIDEO_TEST
assert "totalFrames * 1_000_000L / sampleRate" in CLOCK
from run_source_checks import CHECKS
assert "check_alpha41_replay_timestamps.py" in CHECKS
assert "python3 scripts/run_source_checks.py" in RELEASE
assert "android.permission.RECORD_AUDIO" in MANIFEST
assert "android.permission.QUERY_ALL_PACKAGES" not in MANIFEST
# Real emulator screenshot review: no literal backslash-n shown to users,
# and standalone AMOLED activities have black Android system bars.
for name in ("CaptureStudioActivity.kt", "CaptureStudioRepository.kt"):
    assert r"\\n" not in (SRC / name).read_text(), name
for name in ("CaptureStudioActivity.kt", "GameToolboxActivity.kt"):
    activity = (SRC / name).read_text()
    assert "window.statusBarColor = Color.BLACK" in activity
    assert "window.navigationBarColor = Color.BLACK" in activity
print("alpha.41 Replay timeline and screenshot-derived UI regressions: PASS")
