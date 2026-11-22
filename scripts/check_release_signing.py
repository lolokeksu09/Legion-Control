"""Static release-signing gates; never materializes or prints secret values."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/release-apk.yml").read_text(encoding="utf-8")
verify = (root / "scripts/verify_release_apk.py").read_text(encoding="utf-8")
ignore = (root / ".gitignore").read_text(encoding="utf-8")

names = (
    "LEGION_RELEASE_KEYSTORE_B64",
    "LEGION_RELEASE_STORE_PASSWORD",
    "LEGION_RELEASE_KEY_ALIAS",
    "LEGION_RELEASE_KEY_PASSWORD",
)
for name in names:
    assert name in workflow, name
for name in names[1:]:
    assert f'System.getenv("{name}")' in gradle, name
assert 'System.getenv("LEGION_RELEASE_STORE_FILE")' in gradle
assert 'hasReleaseSigning' in gradle and 'throw GradleException(' in gradle
assert 'signingConfig = signingConfigs.getByName("legionRelease")' in gradle
assert 'versionCode = 27' in gradle
assert 'versionName = "0.1.0-alpha.27"' in gradle
assert "workflow_dispatch:" in workflow
assert "github.ref == 'refs/heads/main'" in workflow
assert "base64 --decode" in workflow
assert "Verify signing secrets are configured" in workflow
assert "verify_release_apk.py" in workflow
assert "EXPECTED_CERT_SHA256" in verify
assert "89e729d207327d682d8b4f74b7ebc663a5764ddcf15155a1801281fc6f739fff" in verify
for fragment in ("*.p12", "*.jks", "*.keystore"):
    assert fragment in ignore, fragment
# Key material must not be shipped with the repository.
assert not list(root.rglob("*.p12"))
assert not list(root.rglob("*.jks"))
print("PASS: permanent release signing is gated on secrets and pinned certificate")
