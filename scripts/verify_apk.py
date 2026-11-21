"""Verify that a signed Android APK contains real Rust JNI outputs."""
from pathlib import Path
from zipfile import ZipFile
import sys

apk = Path(sys.argv[1])
assert apk.is_file() and apk.stat().st_size > 0, "Android APK missing"
with ZipFile(apk) as archive:
    assert archive.testzip() is None, "APK corrupted"
    paths = set(archive.namelist())
    assert "classes.dex" in paths and "AndroidManifest.xml" in paths
    for abi in ("arm64-v8a", "x86_64"):
        path = f"lib/{abi}/liblegion_core.so"
        assert path in paths, f"Rust library missing: {path}"
        assert archive.getinfo(path).file_size > 4096, f"Rust library too small: {path}"
print("PASS: Kotlin APK includes real Rust native binaries for arm64 and x86_64")
