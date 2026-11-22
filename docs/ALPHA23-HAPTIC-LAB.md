# Legion Control v0.1.0-alpha.23 — Haptic Lab

New, standalone haptic diagnostic tool for Android 13+, suitable for different devices and games. This is **not** a duplicate of the existing Audio Lab, Touch/Frame Diagnostics or Gamepad Live.

## Features

- **Real short vibration patterns:** TAP (55ms), DOUBLE_TAP (55ms, 115ms gap, 55ms), TRIPLE_PULSE (three short pulses), SLOW_PULSE (two longer pulses) and LONG_PULSE (880ms).
- **Manual digital amplitude choice:** GENTLE 48/255, MEDIUM 96/255, STRONG 160/255. No infinite repeating haptics; all patterns end within 1.8 seconds.
- **Amplitude capability fallback:** if the Android `Vibrator` does not support amplitude control, use the platform's `DEFAULT_AMPLITUDE` for active intervals and zero for gaps, explicitly report this. Never claim the fallback equals the selected intensity.
- **Hardware capability snapshot:** vibrator IDs/count from `VibratorManager`, `hasVibrator()`, `hasAmplitudeControl()`, `areEffectsSupported` for CLICK/DOUBLE_CLICK/TICK/HEAVY_CLICK, `arePrimitivesSupported` for CLICK/TICK, and Android's `HAPTIC_FEEDBACK_ENABLED` setting. Display as a read-only capability snapshot.
- **User-controlled playback:** only after tapping Start or a preset. Stop button and screen exit cancel the vibrator request. No hidden service, background scheduler, overlay or recording.
- **Subjective tactile diary:** after a test, mark FELT / NOT_FELT / UNCERTAIN and optionally add a 200-character note. This is the user's own perception, **not a physical sensor measurement**.
- **Private local history:** SQLite v12 with `haptic_test_runs`, `haptic_feedback` and `haptic_capability_snapshots`. Running rows from a crashed process are relabelled INTERRUPTED when the next test starts, without data loss.
- **Report, comparison, export:** select earlier runs, compare two latest requests, share a text report, delete a chosen run with confirmation, export all haptic metadata and feedback via `ACTION_CREATE_DOCUMENT` CSV.

## How to use

1. Open Legion Control → **HAPTIC LAB · вибромотор, шаблоны и ручная оценка**.
2. Optionally inspect **Android Vibra-capabilities**, then save that capability snapshot if desired.
3. Choose a pattern and intensity. Start with GENTLE if sensitive to vibration.
4. Tap **Запустить тактильный тест** or a preset. The generated waveform is fixed, short and non-repeating.
5. If desired, tap **Остановить вибрацию** or leave the screen.
6. Inspect your saved run; mark whether **you actually felt it**, and save a short note. Compare multiple runs or export metadata CSV.
7. If haptics are disabled by the phone, inspect the system Sound and vibration Settings screen. Legion Control does not silently change OEM haptic/notification settings.

## Interpreting the output

| Field | Source | Limits |
|---|---|---|
| Vibrator IDs / capabilities | Android public `VibratorManager` / `Vibrator` | API response, **not** motor health verification |
| Requested amplitude 48–160 | Local generated waveform | Digital intensity parameter, **not** physical force or calibrated power |
| Completed request | Android accepted `vibrate` without detected exception; bounded duration elapsed | **Not** proof the motor moved or pattern was felt |
| Android haptic setting | `Settings.System.HAPTIC_FEEDBACK_ENABLED` if accessible | Other OEM overrides, notification/Do Not Disturb, accessibility settings may still matter |
| Subjective rating | User-entered, voluntary | Not objective haptic quality or latency |
| Pattern duration | App's fixed timings | Not measured physical onset delay or exact frequency |

No microhone recording, accelerometer vibration calibration, vibration spectrum sensor, other-app haptic interception, privileged OEM GameTime functionality or changes to system haptic settings.

## Privacy, permissions and compatibility

- Existing app ID `dev.lolokeksu.legioncontrol`, versionCode 23, Android 13+, ARM64/x86_64 native Rust.
- **One new normal Android permission:** `android.permission.VIBRATE` to request haptic feedback. It is not a dangerous runtime dialog or Root/Shizuku privilege. No microphone, location, all-package visibility or other new permissions.
- SQLite v11 → v12 is additive; all prior game sessions, battery history, connectivity data, experiments, application library and audio test history are retained.
- Deleting a haptic run deletes only its subjective feedback for that run. Saved capability snapshots remain until the app's database is cleared.
- Notes and generated reports remain local; sharing or CSV export requires a user-initiated Android action.
- Use the permanently signed alpha.23 APK to update over alpha.22 **without uninstalling** the old release.
- **Manual hardware tests NOT performed by CI:** install on Lenovo Legion Y70 running seller Motorola firmware; verify Haptic Lab launches, default motor exists, all five patterns felt/not felt, stopping/return Home, respecting platform settings, subjective diary, CSV and preservation of earlier data. If the physical device does not vibrate, a green CI is not proof that the feature works on that device.

[Plan-first design and verification evidence](ALPHA23-HAPTIC-LAB-PLAN.md).
