"""Adaptive Replay heap budgets and trim handling remain bounded and keyframe-safe."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
service = (src / "ReplayCaptureService.kt").read_text(encoding="utf-8")
video = (src / "ReplayFrameBuffer.kt").read_text(encoding="utf-8")
audio = (src / "ReplayAudioBuffer.kt").read_text(encoding="utf-8")
audio_capture = (src / "ReplayAudioCapture.kt").read_text(encoding="utf-8")
policy = (src / "ReplayMemoryPolicy.kt").read_text(encoding="utf-8")
video_tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/ReplayFrameBufferTest.kt").read_text(encoding="utf-8")
audio_tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/ReplayAudioBufferTest.kt").read_text(encoding="utf-8")
policy_tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/ReplayMemoryPolicyTest.kt").read_text(encoding="utf-8")
assert "Runtime.getRuntime().maxMemory()" in service
assert "activityManager.isLowRamDevice" in service
assert "ReplayMemoryPolicy.videoByteLimit" in service
assert "ReplayMemoryPolicy.audioByteLimit" in service
assert "ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW" in service
assert "trimRequested.compareAndSet(true, false)" in service
assert "reduceByteLimit" in video and "hasSecondKey()" in video
assert "keyFrameCount > 1" in video and "keyFrameCount--" in video and "keyFrameCount = 0" in video
assert "val available = snapshot()" not in video
assert "nextSaveProbeAt = now + 100L" in service
assert "liveWindow = buffer.stats()" in service and "nextStatsAt = now + 1_000L" in service
assert "statsReflectRetainedFramesAndReducedLimit" in video_tests
assert "byteCount + frozenBytes - sharedBytes" in video
assert "buffer.freezeAt(nowVideoUs)" in service and "finishExport(lease, audioExport)" in service
assert "lease.close()" in service and "exporting.set(false)" in service
assert "audioExport?.close()" in service
assert "finishExport(lease, acquiredAudio)" in service
assert "size + frozenBytes - sharedBytes" in audio
assert "buffer.freezeForVideo(" in audio_capture
assert "buffer.appendEncoded(info.size, info.presentationTimeUs)" in audio_capture
assert "liveAudioWindow = playbackAudio?.stats()" in service
assert "if (!canAdmit(size, ptsUs, false)) return false" in audio
for token in ("liveAndFrozenAudioShareOneBudget", "trimmingAndProducerShutdownKeepOnlyOwnedAudioUntilRelease",
              "rejectedAacPacketsDoNotInvokeCopyAndPtsStayOriginal"):
    assert token in audio_tests, token
assert "frozenExportAndLiveWindowShareOneByteBudget" in video_tests
assert "pressureKeepsFrozenBytesButStopsGrowingUntilRelease" in video_tests
assert "buffer.addEncoded(info.size, info.presentationTimeUs" in service
assert "if (!canAdmit(size, ptsUs, keyFrame, false)) return false" in video
assert video.index("if (!canAdmit(size, ptsUs, keyFrame, false))") < video.index("val bytes = copyBytes()")
assert "rejectedCodecSamplesDoNotAllocateAndResumeFromSync" in video_tests
assert "snapshotForVideo(" in audio and "freezeSnapshot(" in audio_capture
assert "buffer.snapshot(0L, Long.MAX_VALUE)" not in audio_capture
assert "videoClockSnapshotSelectsOnlyMatchingPackets" in audio_tests
assert "reduceByteLimit" in audio and "packets.removeFirst()" in audio
assert "ReplayMemoryPolicy.afterAudioTrim" in audio_capture
assert "ReplayFrameBuffer(windowSeconds, videoLimit)" in service
assert "windowSeconds, audioLimit" in service
for token in ("videoBudgetScalesWithHeapAndLowRamClass", "audioBudgetIsSmallerAndHeapRelative",
              "trimNeverRaisesTheCurrentBudget", "rejectsInvalidHeapAndCurrentBudgets"):
    assert token in policy_tests, token
assert "memoryPressureDropsWholeGopsAndNeverRaisesTheLimit" in video_tests
assert "memoryPressureDropsOldestAudioPacketsAndKeepsTheNewBudget" in audio_tests
print("PASS: heap-relative Replay budgets, low-RAM scaling and keyframe-safe memory trimming")
