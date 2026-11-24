#!/usr/bin/env python3
"""Generate runtime-resolution CycloneDX 1.5 inventories, not APK byte-level attestations."""
import argparse
import json
from pathlib import Path
import re
import tomllib
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]
CONFIGS = ("debugRuntimeClasspath", "releaseRuntimeClasspath")

def component(group, name, version, ecosystem):
    if not all(isinstance(v, str) and v.strip() for v in (name, version)):
        raise ValueError("Missing dependency identity")
    if ecosystem == "maven":
        if not isinstance(group, str) or not group.strip():
            raise ValueError("Missing Maven group")
        purl = "pkg:maven/" + quote(group, safe="") + "/" + quote(name, safe="") + "@" + quote(version, safe="")
    else:
        purl = "pkg:cargo/" + quote(name, safe="") + "@" + quote(version, safe="")
    item = {"type": "library", "name": name, "version": version, "bom-ref": purl, "purl": purl}
    if group:
        item["group"] = group
    return item

def generate(inventory, cargo, app_version, config, revision):
    if set(inventory) != set(CONFIGS):
        raise ValueError("Unexpected runtime configuration inventory")
    if not inventory[config]:
        raise ValueError("Android runtime dependency inventory is empty")
    packages = cargo["package"]
    native = [p for p in packages if p["name"] == "legion-core" and not p.get("source")]
    if len(native) != 1:
        raise ValueError("Missing or ambiguous local Rust package")
    items = [component(p["group"], p["name"], p["version"], "maven") for p in inventory[config]]
    for p in packages:
        if p is native[0]:
            items.append(component("", p["name"], p["version"], "cargo"))
        elif p.get("source", "").startswith("registry+"):
            item = component("", p["name"], p["version"], "cargo")
            if p.get("checksum"):
                item["hashes"] = [{"alg": "SHA-256", "content": p["checksum"]}]
            items.append(item)
        else:
            raise ValueError("Unsupported Cargo source: inventory needs explicit handling")
    unique = {}
    for item in items:
        key = item["bom-ref"]
        if key in unique and unique[key] != item:
            raise ValueError("Conflicting package identities")
        unique[key] = item
    return {
        "bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1,
        "metadata": {"component": {
            "type": "application", "name": "dev.lolokeksu.legioncontrol",
            "version": app_version, "bom-ref": "legion-control:" + config,
            "properties": [{"name": "legion:configuration", "value": config},
                           {"name": "legion:revision", "value": revision},
                           {"name": "legion:scope", "value": "resolved runtime coordinates and Cargo.lock; not APK composition"}]
        }},
        "components": [unique[key] for key in sorted(unique)]
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.revision):
        raise SystemExit("Expected full Git revision")
    inventory = json.loads((ROOT / "app/build/reports/dependency-inventory/android-runtime.json").read_text())
    cargo = tomllib.loads((ROOT / "rust-core/Cargo.lock").read_text())
    app = (ROOT / "app/build.gradle.kts").read_text()
    version = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', app, re.M).group(1)
    args.output.mkdir(parents=True, exist_ok=True)
    for config in CONFIGS:
        bom = generate(inventory, cargo, version, config, args.revision)
        path = args.output / (config + ".cdx.json")
        path.write_text(json.dumps(bom, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{path.name}: {len(bom['components'])} components")

if __name__ == "__main__":
    main()
