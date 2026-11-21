"""Fail if a published APK is unsigned or signed with an unexpected identity."""
from pathlib import Path
import re
import subprocess
import sys

EXPECTED_CERT_SHA256 = "89e729d207327d682d8b4f74b7ebc663a5764ddcf15155a1801281fc6f739fff"

def check_release(apk: Path, apksigner: str) -> None:
    assert apk.is_file() and apk.stat().st_size > 0, "Missing release APK"
    result = subprocess.run(
        [apksigner, "verify", "--verbose", "--print-certs", str(apk)],
        capture_output=True, text=True, check=False
    )
    if result.returncode != 0:
        raise RuntimeError("APK signature verification failed: " + result.stderr[:800])
    match = re.search(
        r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})",
        result.stdout
    )
    if not match:
        raise RuntimeError("Cannot read signing certificate from apksigner")
    actual = match.group(1).lower()
    if actual != EXPECTED_CERT_SHA256:
        raise RuntimeError(
            "Release signing certificate MISMATCH. Refusing to publish an APK "
            "that would break update compatibility."
        )
    print("PASS: release APK signature valid and permanent certificate unchanged")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: verify_release_apk.py APK_PATH APKSIGNER_PATH")
    check_release(Path(sys.argv[1]), sys.argv[2])
