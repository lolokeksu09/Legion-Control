"""Regression source wiring for durable manual settings snapshots and readback."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
controls = (src / "SmartSystemControls.kt").read_text(encoding="utf-8")
policy = (src / "VerifiedSettingWrite.kt").read_text(encoding="utf-8")
timeout = controls[controls.index("fun setScreenTimeout"):controls.index("fun setBrightness")]
brightness = controls[controls.index("fun setBrightness"):controls.index("private fun writeVerified")]
restore = controls[controls.index("fun restoreScreenSettings"):]
assert ".commit()" in timeout and timeout.index(".commit()") < timeout.index("writeVerified(")
assert ".commit()" in brightness and brightness.index(".commit()") < brightness.index("writeVerified(")
assert ".apply()" not in timeout + brightness + restore
assert restore.index("SCREEN_BRIGHTNESS,") < restore.index("SCREEN_BRIGHTNESS_MODE,") < restore.index('remove("brightness")')
assert restore.index("SCREEN_OFF_TIMEOUT,") < restore.index('remove("timeout")')
assert "check(actual == requested)" in policy and "check(write(requested))" in policy
assert "Build.VERSION.SDK_INT >= 35" in controls and "другие правила Android" in controls
print("PASS: durable manual settings snapshots, readback, independent retry, Android 15 DND feedback")
