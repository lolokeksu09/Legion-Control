#!/usr/bin/env python3
"""Alpha.37 Clip Workshop: ownership, non-destructive media lifecycle, bounded exports."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
read = lambda name: (src / name).read_text(encoding="utf-8")
media = read("ClipWorkshop.kt")
policy = read("ClipWorkshopPolicy.kt")
repo = read("CaptureStudioRepository.kt")
vm = read("CaptureStudioViewModel.kt")
ui = read("CaptureStudioActivity.kt")
video_store = read("CaptureClipLibrary.kt")
image_store = read("CaptureScreenshotLibrary.kt")
unit = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/ClipWorkshopPolicyTest.kt").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
permissions = {n.get(ns+"name") for n in manifest.findall("uses-permission")}
assert "android.permission.READ_MEDIA_VIDEO" not in permissions
assert "android.permission.MANAGE_EXTERNAL_STORAGE" not in permissions
assert "android.permission.WRITE_SECURE_SETTINGS" not in permissions
assert len(manifest.findall("./application/receiver")) == 0

assert "fun custom(" in policy and "fun first(" in policy and "fun last(" in policy
assert "fun requireSyncStartUs(" in policy
assert "fun normalizePtsUs(" in policy
assert "MAX_EXPORT_MS = 15 * 60_000L" in policy
assert "MAX_SAMPLE_BYTES = 8 * 1024 * 1024" in policy
assert unit.count("@Test") >= 12
assert "nonSyncFrameIsRejected" in unit
assert "outOfBoundsEndIsRejected" in unit

assert "requireOwnClip(clip).uri" in repo
assert "clips().firstOrNull { it.uri == clip.uri }" in repo
assert "ClipWorkshop.trim(" in repo and "ClipWorkshop.exportFrame(" in repo
assert "CaptureClipLibrary.delete(app, clip.uri)" in repo
assert "CaptureClipLibrary.listOwn(app)" in repo
assert "OWNER_PACKAGE_NAME" in video_store and "OWNER_PACKAGE_NAME" in image_store
assert 'const val RELATIVE_DIR = "Movies/LegionControl/"' in video_store
assert 'const val RELATIVE_DIR = "Pictures/LegionControl/"' in image_store

for expected in (
    "MediaExtractor()", "MediaMuxer(", "MediaMetadataRetriever()",
    "MediaExtractor.SEEK_TO_PREVIOUS_SYNC",
    "ClipWorkshopPolicy.requireSyncStartUs(", "ClipWorkshopPolicy.normalizePtsUs(",
    "MediaExtractor.SAMPLE_FLAG_SYNC", "MediaExtractor.SAMPLE_FLAG_ENCRYPTED",
    "MediaCodec.BUFFER_FLAG_KEY_FRAME", "MediaCodec.BUFFER_FLAG_PARTIAL_FRAME",
    "ClipWorkshopPolicy.MAX_SAMPLES_PER_TRACK", "ClipWorkshopPolicy.MAX_SAMPLE_BYTES",
    "CaptureClipLibrary.createPending(", "CaptureClipLibrary.publish(",
    "CaptureClipLibrary.delete(", "CaptureScreenshotLibrary.createPending(",
    "CaptureScreenshotLibrary.publish(", "CaptureScreenshotLibrary.delete(",
    "MediaMetadataRetriever.OPTION_CLOSEST", "Bitmap.CompressFormat.PNG",
    "ensureRunning(cancelled)", "if (!published) output?.let",
    "if (!published) destination?.let", "mux.setOrientationHint",
):
    assert expected in media, expected
assert "fun trim(" in media and "fun exportFrame(" in media
assert "source" in media and "range" in media
assert media.index("videoExtractor.seekTo(") < media.index("CaptureClipLibrary.createPending(")
assert media.index("ensureRunning(cancelled)\n            CaptureClipLibrary.publish(") > media.index("mux.stop()")
assert "val audioFormat" in media and "copyWithinRange(audioExtractor," in media
assert 'check(written <= ClipWorkshopPolicy.MAX_SAMPLES_PER_TRACK)' in media

for expected in (
    "viewModel.saveClipExcerpt(clip,", "viewModel.saveClipFrame(clip,",
    "chooseExcerptRange(clip)", "chooseVideoFrame(clip)",
    "viewModel.cancelClipEdit()", "confirmDelete(clip)",
):
    assert expected in ui, expected
assert "CaptureClipLibrary.createPending(" not in ui
assert "CaptureScreenshotLibrary.createPending(" not in ui
assert "MediaExtractor" not in ui and "MediaMuxer" not in ui
assert "AtomicReference<AtomicBoolean?>" in vm
assert "currentEdit.compareAndSet(null, token)" in vm
assert "token.get() || Thread.currentThread().isInterrupted" in vm
assert "currentEdit.get()?.set(true)" in vm
assert "repository.trimOwnClip(" in vm and "repository.exportOwnClipFrame(" in vm
assert "worker.execute {" in vm
for forbidden in (
    "Runtime.getRuntime", "java.net.", "MediaProjectionManager",
    "getMediaProjection", "AudioRecord", "AccessibilityService", "import rikka.shizuku",
    "android.os.Process.killProcess",
):
    assert forbidden not in media + policy
print("PASS alpha37: owned-only MP4 excerpt and PNG frame exports, sample bounds, "
      "sync-frame clipping, cancellable async cleanup, unchanged permissions")
