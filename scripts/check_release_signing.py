"""Static release-signing gates; never materializes or prints secret values."""
from pathlib import Path
import re

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
assert 'versionCode = 59' in gradle
assert 'versionName = "0.1.0-alpha.59"' in gradle
assert "workflow_dispatch:" in workflow
assert re.search(r"^  push:\n    branches: \[main\]", workflow, re.MULTILINE)
assert "github.event_name == 'push' && contains(github.event.head_commit.message, '[signed-apk]')" in workflow
assert workflow.index("Wait for exact main commit Android CI before permanent signing") < workflow.index("Restore private release keystore outside the repository")
assert "type: boolean" in workflow and "default: false" in workflow
assert workflow.count("if: github.event_name == 'workflow_dispatch' && inputs.publish == true") == 1
assert workflow.index("if: github.event_name == 'workflow_dispatch' && inputs.publish == true") > workflow.index("Publish pre-release with permanent certificate")
assert "refusing to replace binaries" in workflow
assert "cancel-in-progress: false" in workflow
assert "tag=v0.1.0-alpha.59" in workflow
assert "Legion-Control-v0.1.0-alpha.59-signed.apk" in workflow
assert "--event push --branch main" in workflow
assert "--json headSha,headBranch,event,status,conclusion" in workflow
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
# Prevent mutable third-party workflow refs and accidental credential persistence.
for workflow_path in (".github/workflows/android.yml", ".github/workflows/release-apk.yml"):
    body = (root / workflow_path).read_text(encoding="utf-8")
    uses = re.findall(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)", body, re.MULTILINE)
    assert uses and all(re.fullmatch(r"[^@]+@[0-9a-f]{40}", ref) for ref in uses), workflow_path
    assert body.count("persist-credentials: false") == sum(ref.startswith("actions/checkout@") for ref in uses)
    assert "toolchain: '1.100.0'" in body
assert "Remove temporary permanent signing keystore" in workflow
assert 'run: rm -f -- "$LEGION_RELEASE_STORE_FILE"' in workflow
print("PASS: permanent release signing is gated on secrets and pinned certificate")
