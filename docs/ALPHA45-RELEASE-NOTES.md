# Legion Control v0.1.0-alpha.45

Signed distribution of the UI improvements integrated into `main` as
[`02980d7`](https://github.com/lolokeksu09/Legion-Control/commit/02980d76d2079731a11fd1b6f88a89d352efb77d)
(PR #49). The old alpha.44 release is preserved unchanged.

## Improved
- Capture Studio: asynchronously populated MP4 and PNG lists use the common
  AMOLED card/button style and have descriptive TalkBack labels.
- Game Session: "НАЧАТЬ ЗАПИСЬ" appears on the first viewport, with inline
  action feedback and optional settings below the main recording controls.
- Home, Game Toolbox and game details share consistent dark card surfaces,
  padding and accessible action descriptions.
- Keeps the unified five-tab UI, existing icons, navigation, and back stack
  established in alpha.44.

## Unchanged
- Application ID, permanent signing certificate, device permissions, user data,
  SQLite schema, recordings, replay/AAC, Kotlin/Rust backends and JNI.
- No new privileged Android functionality and no new forced onboarding.

## Verification
GitHub Actions builds the APK with the protected release certificate, verifies
its pinned public certificate and both Rust JNI ABIs, and publishes only after
Kotlin/Rust CI and Android 15 emulator UI smoke pass on the **exact main SHA**.
The emulator covers navigation and recording control states. Physical Lenovo
Legion Y70 verification and media-library populated-state checks remain separate.
