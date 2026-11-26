#!/usr/bin/env python3
"""Alpha36 permission + runtime path contract: no silent game-audio capture or microphone source."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/dev/lolokeksu/legioncontrol"
read=lambda name:(src/name).read_text(encoding="utf-8")
manifest=ET.parse(root/"app/src/main/AndroidManifest.xml").getroot()
ns="{http://schemas.android.com/apk/res/android}"
perms={x.get(ns+"name") for x in manifest.findall("uses-permission")}
assert "android.permission.RECORD_AUDIO" in perms
assert "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" in perms
for forbidden in ("android.permission.QUERY_ALL_PACKAGES", "android.permission.WRITE_SECURE_SETTINGS",
                  "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_AUDIO",
                  "android.permission.CAPTURE_AUDIO_OUTPUT", "android.permission.MODIFY_AUDIO_ROUTING"):
    assert forbidden not in perms

service=read("CaptureRecordingService.kt")
ui=read("CaptureStudioActivity.kt")
repository=read("CaptureStudioRepository.kt")
vm=read("CaptureStudioViewModel.kt")
audio=read("PlaybackAudioCapture.kt")
mux=read("CaptureMp4Muxer.kt")
policy=read("PlaybackAudioTrackPolicy.kt")
video=read("CaptureClipLibrary.kt")
clock=read("CaptureRecordingClock.kt")
test=(root/"app/src/test/java/dev/lolokeksu/legioncontrol/PlaybackAudioTrackPolicyTest.kt").read_text()

assert 'getBoolean("playback_audio", false)' in repository
assert 'choosePlaybackAudio(enabled: Boolean)' in repository
assert "viewModel.choosePlaybackAudio(!viewModel.playbackAudioEnabled())" in ui
assert "requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PLAYBACK_PERMISSION)" in ui
assert "checkSelfPermission(Manifest.permission.RECORD_AUDIO)" in ui
assert "onRequestPermissionsResult(" in ui
assert "createScreenCaptureIntent()" in ui
assert "CaptureRecordingService.EXTRA_PLAYBACK_AUDIO" in ui
assert "CaptureRecordingService.EXTRA_AUDIO_GAME" in ui
assert "if (!canStartCapture(settings)) return" in ui
assert "if (!canStartCapture(snapshot)) return" in ui
assert "if (settings.selectedGame != null) return true" in ui
assert "if (!settings.profile.playbackAudio) return true" in ui
assert "val audioGame = if (playbackRequested) snapshot.selectedGame else null" in ui
assert "val config = snapshot.profile.toConfig()" in ui
assert "packageManager.getLaunchIntentForPackage(it) != null" in repository
assert "getStringExtra(EXTRA_AUDIO_GAME)" in service
assert "getLaunchIntentForPackage(pkg) != null" in service
assert "selectedGameUid = uid" in service
assert ".addMatchingUid(gameUid)" in audio
assert "putExtra(CaptureRecordingService.EXTRA_PLAYBACK_AUDIO, playbackRequested)" in ui
assert "Manifest.permission.RECORD_AUDIO" in service
assert "intent.getBooleanExtra(EXTRA_PLAYBACK_AUDIO, false)" in service
assert "playbackAudioRequested &&" in service
assert "checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED" in service
assert "PlaybackAudioCapture(this, capture, requireNotNull(selectedGameUid))" in service
assert "it.start(videoStartedAtMs)" in service
assert "runCatching { playbackAudio?.pause() }" in service
assert "runCatching { playbackAudio?.resume() }" in service
assert "audio.stopAndTakeResult()" in service
assert "CaptureMp4Muxer.combine(this, uri, aac," in service
assert "publishVideoOrDelete(uri, videoValid," in service
assert "Thread({" in service and "legion-audio-mp4-finalizer" in service
assert service.index("promoteToForeground()") < service.index("beginRecording(consent, config)")
assert service.count("capture.createVirtualDisplay(") == 1
assert service.index("playbackAudio?.pause()") < service.index("runCatching { previous?.stop() }")
assert "CaptureClipLibrary.delete(this, uri)" in service
assert "CaptureClipLibrary.publish(this, uri)" in service
for x in ("AudioPlaybackCaptureConfiguration.Builder(projection)",
          "addMatchingUsage(AudioAttributes.USAGE_MEDIA)",
          "addMatchingUsage(AudioAttributes.USAGE_GAME)",
          "AudioRecord.Builder()", "setAudioPlaybackCaptureConfig(captureConfig)",
          "MediaFormat.MIMETYPE_AUDIO_AAC", "AACObjectLC",
          "MediaCodec.BUFFER_FLAG_END_OF_STREAM", "AudioRecord.READ_NON_BLOCKING",
          "stopAndTakeResult()", "fun pause()", "fun resume()", "record.release()"):
    assert x in audio, x
assert "CaptureClipLibrary.createPending(" in mux
assert "CaptureClipLibrary.publish(" in mux
assert "CaptureClipLibrary.delete(" in mux
assert 'findTrack(videoExtractor, "video/")' in mux
assert 'findTrack(audioExtractor, "audio/")' in mux
assert "MediaExtractor()" in mux and "MediaMuxer(" in mux
assert "MediaCodec.BufferInfo()" in mux
assert "MediaExtractor.SAMPLE_FLAG_ENCRYPTED" in mux
assert "MediaExtractor.SAMPLE_FLAG_SYNC" in mux and "MediaCodec.BUFFER_FLAG_KEY_FRAME" in mux
assert "MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME" in mux and "MediaCodec.BUFFER_FLAG_PARTIAL_FRAME" in mux
assert "extractor.sampleFlags)" not in mux
assert "MAX_COPY_SAMPLE_BYTES" in mux
assert "PlaybackAudioTrackPolicy.muxPtsUs(" in mux
assert "PlaybackAudioTrackPolicy.framesForBytes(" in audio
assert "PlaybackAudioTrackPolicy.ptsUs(" in audio
assert "48_000" in policy and "AAC_BITRATE = 128_000" in policy
assert "CaptureRecordingClock" in clock and "Movies/LegionControl/" in video
assert test.count("@Test") >= 7
for forbidden in ("MediaRecorder.AudioSource.MIC", "MediaRecorder.AudioSource.VOICE_COMMUNICATION",
                  "MediaRecorder.AudioSource.REMOTE_SUBMIX", "AudioSource.MIC",
                  "Runtime.getRuntime(", "Shizuku", "AccessibilityService", "BOOT_COMPLETED"):
    assert forbidden not in audio+mux+ui+service
print("PASS alpha36: opt-in Android playback audio capture, no microphone, AAC remux and video-only fallback")
