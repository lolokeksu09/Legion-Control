# Legion Control v0.1.0-alpha.22 — Audio Lab

The app now generates original, quiet **short PCM tones** for a manually initiated headphone/speaker channel check. This is new functionality, separate from Compatibility Lab's static Audio Output Inspector.

## Functions

- **Audio signal generator:** fixed 48 kHz stereo PCM16, supported 220, 440, 880 and 1760 Hz; stereo/left/right tests, stereo identification (first left, then right), and 220→1760 Hz sweep.
- **Safety:** ≤10% full-scale generated PCM, 30 ms fade-in/fade-out (including stereo channel switch), playback bounded to 750, 1500 or 3000 ms. Before first test the UI requests confirmation that the user checked the device's system volume. **System volume is never increased automatically.**
- **Android AudioTrack backend:** uses platform audio focus, a foreground-only `AudioTrack` static PCM buffer, observes playback-head position and route type when available. The user can stop immediately or simply leave the activity; no background service.
- **Route inspector:** attached device types (built-in speaker, wired headphones, USB, Bluetooth, HDMI, etc.). This is *a list of available Android outputs*, not proof of current routing. Android's active `AudioTrack.routedDevice` type is captured when known during a playback, separate from attached outputs.
- **Routing events:** while a playback test is active, record public `AudioTrack` route callbacks. The live screen displays attached-output changes through `AudioDeviceCallback`. Listeners stop when the screen leaves the foreground.
- **History:** private SQLite v11 audio_test_runs and audio_route_events, explicit COMPLETE / STOPPED / ERROR / INTERRUPTED state, selection, last-two-run metadata comparison and deletion with confirmation.
- **Export:** CSV through Android's Storage Access Framework, and share a completed text report on user request. Exports contain only playback-test metadata, no microphone audio.

## Usage

1. On the home screen tap **AUDIO LAB · стерео, частоты, маршруты и история**.
2. Reduce the system media volume, especially with headphones. Select a test preset or choose kind, channel, tone frequency and duration.
3. Tap **Воспроизвести**; confirm the safety dialog the first time. Hear the result yourself: Android cannot prove a tone was acoustically emitted or that a specific channel was actually audible.
4. Use Stop to interrupt playback or leave the screen. AudioTrack should release audio focus and stop; partial records are marked STOPPED. An old RUNNING record from a crash is relabelled INTERRUPTED on the next manual start.
5. View connected outputs separately from the routed device reported during a test. Check local test history or compare two recent reports.
6. Export all audio-test metadata in CSV only after choosing a destination document. No auto-upload.

## Important measurement boundaries

| Value | Measured or reported by | Not proof of |
|---|---|---|
| PCM frequency/sample rate | This app's original sample generator | Actual speaker response or calibrated loudness |
| Playback-head frames | Android AudioTrack | End-to-end acoustic latency, DSP delay or earphone output |
| Routed device type | `AudioTrack.routedDevice` if Android supplies it | Actual channel mapping, codec or audible signal |
| Available output list | Android `AudioManager.getDevices` | Device used by this specific test |
| Error / interrupted | Local user action or Android API error | A hardware fault diagnosis |

**No microphone access, recording, audio capture, third-party audio interception, precise latency measurement, Root or Shizuku.** There are no new Android permissions or network endpoints.

## Upgrade compatibility

- Package: `dev.lolokeksu.legioncontrol` (unchanged).
- `versionCode=22`, `versionName=0.1.0-alpha.22`; Android 13+, ARM64 and x86_64.
- SQLite migration v10→v11 **additive** and preserves the existing game/thermal/battery/connectivity/app-library data. Deleting an audio test deletes only its own new route events through the FK.
- The same permanent release certificate must be used for signed APK to install over alpha.21.
- **Physical tests NOT RUN** by GitHub Actions: confirm update over alpha.21 on the Lenovo Legion Y70 with Motorola firmware, click left/right/sweep, verify no audio after Home, unplug headset during test, test Bluetooth, test stop and export CSV. Real sound, audible channel identity and OEM routing still require a person/device.

[Original plan and release gate evidence](ALPHA22-AUDIO-LAB-PLAN.md).
