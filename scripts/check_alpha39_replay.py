#!/usr/bin/env python3
"""alpha.39 replay source wiring guard; hardware behavior requires a real device."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/dev/lolokeksu/legioncontrol"
TEST = ROOT / "app/src/test/java/dev/lolokeksu/legioncontrol"
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
REPLAY = (SRC / "ReplayCaptureService.kt").read_text()
BUFFER = (SRC / "ReplayFrameBuffer.kt").read_text()
EXPORT = (SRC / "ReplayExporter.kt").read_text()
STUDIO = (SRC / "CaptureStudioActivity.kt").read_text()
TOOLBOX = (SRC / "GameToolboxService.kt").read_text()
RECORD = (SRC / "CaptureRecordingService.kt").read_text()
SHOT = (SRC / "CaptureScreenshotService.kt").read_text()
TESTS = (TEST / "ReplayFrameBufferTest.kt").read_text()

assert 'android:name=".ReplayCaptureService"' in MANIFEST
assert 'android:foregroundServiceType="mediaProjection"' in MANIFEST
for required in (
    "MediaProjectionManager", "createVirtualDisplay", "MediaCodec",
    "ReplayFrameBuffer", "ReplayExporter.save", "ACTION_SAVE", "ACTION_STOP",
    "startForeground(", "signalEndOfInputStream", "onStop()",
):
    assert required in REPLAY, required
for required in (
    "byteLimit", "keyFrame", "hasSecondKey()", "clear()",
    "windowSeconds", "snapshot()",
):
    assert required in BUFFER, required
for required in (
    "MediaMuxer", "createPending", "publish", "delete", "writeSampleData",
):
    assert required in EXPORT, required
assert "REQUEST_REPLAY" in STUDIO
assert "rememberReplayConsent" in STUDIO
assert "startForegroundService(start)" in STUDIO
assert "ReplayCaptureService.save(this)" in TOOLBOX
assert "ReplayCaptureService.active" in RECORD
assert "ReplayCaptureService.active" in SHOT
assert "ReplayFrameBufferTest" in TESTS
assert "trimsToNextSyncFrameWithoutRetainingUndecodablePrefix" in TESTS
assert "cappedBytesDiscardOnlyWholeKeyframeGroups" in TESTS
AUDIO = (SRC / "ReplayAudioCapture.kt").read_text()
AUDIO_BUFFER = (SRC / "ReplayAudioBuffer.kt").read_text()
AUDIO_TEST = (TEST / "ReplayAudioBufferTest.kt").read_text()
assert "AudioPlaybackCaptureConfiguration.Builder(projection)" in AUDIO
assert ".addMatchingUid(selectedGameUid)" in AUDIO
assert "setAudioPlaybackCaptureConfig(playback)" in AUDIO
assert "ReplayAudioBuffer(seconds, maxBufferBytes)" in AUDIO
assert "RECORD_AUDIO" in STUDIO and "REQUEST_REPLAY_AUDIO_PERMISSION" in STUDIO
assert "EXTRA_PLAYBACK_AUDIO" in REPLAY and "ReplayAudioCapture" in REPLAY
assert "ReplayAudioSnapshot" in EXPORT and "writer.addTrack(it.format)" in EXPORT
assert "maxBytes" in AUDIO_BUFFER and "snapshot(startUs" in AUDIO_BUFFER
assert "ReplayAudioBufferTest" in AUDIO_TEST
assert "audio capture is optional" in (
    ROOT / "docs/ALPHA39-REPLAY-ENGINE.md").read_text().lower()
assert "android.permission.QUERY_ALL_PACKAGES" not in MANIFEST
assert "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" in MANIFEST
for forbidden in ("Runtime.getRuntime().exec(", "Shizuku", "MediaRecorder(",
                  "MediaProjection.createScreenCaptureIntent"):
    assert forbidden not in REPLAY, forbidden
print("alpha.39 Replay + opt-in UID-filtered AAC wiring: PASS")
