#!/usr/bin/env python3
"""Source contract for shared capture status, commands and media UI."""
from pathlib import Path
src = Path(__file__).resolve().parents[1] / "app/src/main/java/dev/lolokeksu/legioncontrol"
ui = (src / "CaptureStudioActivity.kt").read_text()
toolbox = (src / "GameToolboxFragment.kt").read_text()
assert "val (mode, phase) = CaptureCoordinator.state()" in ui
assert "val idle = mode == null && !recording && !replay && !screenshot" in ui
assert "listOf(recordStart, replayStart, shotStart).forEach { it.isEnabled = idle }" in ui
assert "CapturePhase.STOPPING" in ui
assert "uiHandler.removeCallbacks(stateTick)" in ui[ui.index("override fun onPause()"):]
assert "if (paused) CaptureRecordingService.resume(this)" in ui
assert "else CaptureRecordingService.pause(this)" in ui
assert 'action("ОБНОВИТЬ МЕДИАТЕКУ", "capture.clips")' in ui
assert "browseClips()\n            viewModel.refreshScreenshots()" in ui
assert "clipState.text = if (clips.isEmpty())" in ui
assert "shotState.text = if (shots.isEmpty())" in ui
assert "Intent(requireContext(), CaptureStudioActivity::class.java)" in toolbox
for mode in ("RECORDING", "REPLAY", "SCREENSHOT"):
    assert f"reserveConsent(CaptureMode.{mode})" in ui
print("PASS: shared capture controls and separate library states; fresh consent retained")
