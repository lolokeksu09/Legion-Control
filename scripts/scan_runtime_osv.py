#!/usr/bin/env python3
"""Fail closed on OSV findings or incomplete scans of resolved runtime dependencies."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sys
import urllib.request

API = "https://api.osv.dev/v1/querybatch"
CONFIGS = ("debugRuntimeClasspath", "releaseRuntimeClasspath")

def inventory(directory):
    packages, local, revisions = {}, set(), set()
    for config in CONFIGS:
        path = directory / (config + ".cdx.json")
        bom = json.loads(path.read_text(encoding="utf-8"))
        props = {p["name"]: p["value"] for p in bom["metadata"]["component"]["properties"]}
        if props["legion:configuration"] != config:
            raise ValueError("Unexpected SBOM configuration")
        revisions.add(props["legion:revision"])
        components = bom["components"]
        if not components:
            raise ValueError("Empty SBOM")
        for item in components:
            purl = item.get("purl")
            if not purl and item["bom-ref"].startswith("local:legion-core:"):
                local.add(item["bom-ref"])
                continue
            if not isinstance(purl, str) or not purl.startswith(("pkg:maven/", "pkg:cargo/")):
                raise ValueError("Unknown package identity")
            packages.setdefault(purl, set()).add(config)
    if len(revisions) != 1 or not re.fullmatch("[a-f0-9]{40}", next(iter(revisions))):
        raise ValueError("SBOM revisions differ or invalid")
    if not packages:
        raise ValueError("No external dependencies to scan")
    return packages, sorted(local), next(iter(revisions))

def request_batch(queries):
    body = json.dumps({"queries": queries}).encode("utf-8")
    request = urllib.request.Request(API, data=body,
        headers={"Content-Type": "application/json", "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=45) as response:
        raw = response.read(4*1024*1024+1)
    if len(raw) > 4*1024*1024:
        raise ValueError("OSV response exceeds limit")
    return json.loads(raw)

def scan(packages, query=request_batch):
    findings = {p: set() for p in sorted(packages)}
    pending = [(p, {"package": {"purl": p}}) for p in findings]
    seen_tokens = set()
    for _ in range(20):
        if not pending:
            return {p: sorted(ids) for p, ids in findings.items()}
        following = []
        for start in range(0, len(pending), 100):
            chunk = pending[start:start+100]
            response = query([q for _, q in chunk])
            results = response.get("results")
            if not isinstance(results, list) or len(results) != len(chunk):
                raise ValueError("Incomplete OSV batch result")
            for (p, q), result in zip(chunk, results):
                if not isinstance(result, dict) or set(result)-{"vulns","next_page_token"}:
                    raise ValueError("Invalid OSV result")
                vulnerabilities = result.get("vulns", [])
                if not isinstance(vulnerabilities, list):
                    raise ValueError("Invalid OSV vulnerability list")
                for vulnerability in vulnerabilities:
                    identifier = vulnerability["id"]
                    if not isinstance(identifier, str) or not identifier.strip():
                        raise ValueError("Invalid OSV advisory ID")
                    findings[p].add(identifier)
                token = result.get("next_page_token")
                if token:
                    if not isinstance(token, str) or (p, token) in seen_tokens:
                        raise ValueError("Invalid or repeated OSV pagination token")
                    seen_tokens.add((p, token))
                    following.append((p, {**q, "page_token": token}))
        pending = following
    if pending:
        raise ValueError("Incomplete paginated OSV scan")
    return {p: sorted(ids) for p, ids in findings.items()}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sbom-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = {"api": API, "checked_at_utc": datetime.now(timezone.utc).isoformat(),
              "status": "error"}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        packages, local, revision = inventory(args.sbom_dir)
        report.update(revision=revision, scanned_external_components=len(packages),
                      excluded_local_components=local)
        results = scan(packages)
        report["findings"] = [{"purl": p, "configurations": sorted(packages[p]), "advisories": ids}
                              for p, ids in results.items() if ids]
        report["status"] = "findings" if report["findings"] else "pass"
        print("OSV:", report["status"], len(packages), "external components,",
              len(report["findings"]), "affected package versions")
    except Exception as exc:
        report["error"] = type(exc).__name__ + ": " + str(exc)
        print("OSV scan incomplete:", report["error"], file=sys.stderr)
    finally:
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0 if report["status"] == "pass" else 1

if __name__ == "__main__":
    sys.exit(main())
