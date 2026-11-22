# Legion Control alpha.37 — Clip Workshop (real media editing, no diagnostics)

## New functionality

Capture Studio's **own saved MP4** library now offers actions to:
- Export a separate MP4 containing the **first 15 seconds**, **last 15 seconds** or **last 30 seconds**;
- Specify a custom start/end in whole seconds to export an independent excerpt (at least 1 second, at most 15 minutes);
- Export the **first**, **middle**, or chosen timestamp as a separate full-quality PNG (resized only if its longer edge exceeds 1920px);
- Cancel a running clip edit. Failed/cancelled exports delete the pending output and do not remove or overwrite the original MP4.

All edits happen on an Android background executor, with a single active media job per ViewModel. The source URI must be validated against the exact package-owned published MediaStore clip listing before an edit starts. No third-party videos, broad gallery scanning or automatic processing.

## Technical design and limitations

The MP4 excerpt operation uses the public Android **MediaExtractor + MediaMuxer** APIs. This is a *copy of already encoded tracks*, not a transcoding pipeline: it preserves encoded video and, if the file has one, its first audio track. It also preserves the container's rotation hint. No Root, Shizuku, microphone, MediaProjection token, cloud service or extra permissions.

**Decodability over frame-exact edits**: MediaExtractor seeks to the previous video sync (key) frame. The exported MP4 therefore starts at this earlier boundary, which can be before the requested start time. Audio samples preceding the selected video sync timestamp are dropped; both track timelines are normalized relative to that sync timestamp. The end boundary is enforced as an upper bound. This editor does **not** promise frame-accurate arbitrary cuts or re-encoding.

- Invalid ranges, empty/no-sync video, encryption, out-of-bounds timestamps and oversized samples are rejected.
- Export range cap: 15 minutes; per-track max 200,000 encoded samples; per-sample max 8 MiB.
- `IS_PENDING=1` is used until a complete MP4/PNG is written. The incomplete output is removed on failure/cancellation. The original is never deleted by the editor.
- A protected video or unsupported MP4 codec may fail. The user sees an error; no fallback modifies the source.
- A video frame is decoded with `MediaMetadataRetriever.getFrameAtTime` and exported into `Pictures/LegionControl/`. No screen-recording permission is needed because this reads an already recorded clip.

## Acceptance and evidence

- Pure JVM `ClipWorkshopPolicyTest`: valid first/last/custom ranges, one-second minimum, 15-minute cap, negative and over-duration cases, synchronization requirements, sample bounds, PNG timestamp validation.
- Strict static `scripts/check_alpha37_clip_workshop.py`: ownership, no new permissions, time/size bounds, original preserved, MediaStore pending creation/publication/deletion, cancellation and background work.
- Existing full Android CI: all version-pinned contracts, Android Kotlin/JUnit, Rust JNI, Android APK lint, signing safety, Android 35 emulator smoke.

**Real-device completion remains separate:** test an MP4 with audible AAC recorded by the app and one without audio, verify playable excerpts and audio alignment in a video player, verify rotation, edit at a non-keyframe boundary, export a first/middle PNG, cancel a long trim and confirm the original remains, close/reopen the app, and inspect MediaStore for incomplete output. Device target is Lenovo Legion Y70 with Motorola ROM. Generic emulator startup smoke alone does not exercise MediaExtractor/MediaMuxer.

No SQLite migration, permission addition, Rust/ABI change, signing change or new diagnostic feature. The application version remains pinned at `0.1.0-alpha.27` until a separate coordinated release task.
