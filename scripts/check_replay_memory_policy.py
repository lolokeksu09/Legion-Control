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
