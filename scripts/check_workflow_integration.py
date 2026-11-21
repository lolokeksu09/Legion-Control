#!/usr/bin/env python3
"""L1: verify the active workflow standard is wired into existing Legion Control."""
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
active = ["AGENTS.md", "PROJECT.md", "TESTING-LEVELS.md", "docs/WORKFLOW-ADOPTION.md"]
for name in active:
    assert (root / name).is_file(), f"missing active document: {name}"

# The source template stays immutable and internally consistent.
source = root / "workflow-standard"
assert (source / "tools/check_standard.py").exists()
result = subprocess.run([sys.executable, str(source / "tools/check_standard.py"), str(source)],
                        capture_output=True, text=True, check=False)
assert result.returncode == 0, "source template validation failed:\n" + result.stdout + result.stderr
assert "8/8 checks passed" in result.stdout

pattern = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
for name in active:
    content = (root / name).read_text(encoding="utf-8")
    location = PurePosixPath(name).parent
    for ref in pattern.findall(content):
        location_ref = ref.split("#", 1)[0].strip()
        if not location_ref or re.match(r"^[a-z][a-z0-9+.-]*:", location_ref, re.I):
            continue
        parts = []
        for part in (location / location_ref).parts:
            if part == "..":
                assert parts, f"path escapes repo: {name} -> {ref}"
                parts.pop()
            elif part not in ("", "."):
                parts.append(part)
        target = root.joinpath(*parts)
        assert target.exists(), f"broken document link: {name} -> {ref}"

project = (root / "PROJECT.md").read_text(encoding="utf-8")
agents = (root / "AGENTS.md").read_text(encoding="utf-8")
testing = (root / "TESTING-LEVELS.md").read_text(encoding="utf-8")
readme = (root / "README.md").read_text(encoding="utf-8")
ci = (root / ".github/workflows/android.yml").read_text(encoding="utf-8")
assert "dev.lolokeksu.legioncontrol" in project
assert "Kotlin" in agents and "Rust" in agents and "JNI" in agents
assert "L1" in testing and "L5" in testing
assert "Project Workflow Standard v1.0" in readme
for command in (
    "python3 scripts/check_backend.py", "bash scripts/build-native.sh",
    "assembleDebug lintDebug", "python3 scripts/verify_apk.py",
    "python3 scripts/check_workflow_integration.py",
    "python3 workflow-standard/tools/check_standard.py workflow-standard",
):
    assert command in ci, f"CI gate removed or missing: {command}"
assert "actions/upload-artifact" in ci
print("PASS: v1.0 standard intact, active rules linked, Kotlin/Rust Android CI preserved")
