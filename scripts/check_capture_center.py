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
repo = (src / "CaptureStudioRepository.kt").read_text()
media = (src / "CaptureMediaLibrary.kt").read_text()
vm = (src / "CaptureStudioViewModel.kt").read_text()
assert "CaptureStudioPane.initial(" in ui and "FeatureReveal.fromActivity(" in ui
assert "viewModel.library.observe(this, ::renderMedia)" in ui
assert "CaptureLibraryState.Loading" in ui and "CaptureLibraryState.Error" in ui
assert "CaptureMediaLibrary.requirePublished(app, item)" in repo
assert 'openFileDescriptor(uri, "r")' in repo
assert 'LIKE ? ESCAPE' in media and "IS_TRASHED" in media
assert "putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS" in media
assert "token != revision.get()" in vm and "request != accessGeneration" in vm
assert "Intent(requireContext(), CaptureStudioActivity::class.java)" in toolbox
for mode in ("RECORDING", "REPLAY", "SCREENSHOT"):
    assert f"reserveConsent(CaptureMode.{mode})" in ui
print("PASS: separate media destinations, owned paged results, cancellable consent and file access")
