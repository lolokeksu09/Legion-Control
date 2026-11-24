#!/usr/bin/env python3
"""Actual ADB measurements for the installed APK. No root, settings writes or game data."""
from __future__ import annotations

import hashlib
import json
import re
import statistics
import subprocess
import time
from pathlib import Path

PACKAGE = "dev.lolokeksu.legioncontrol"


def startup_metrics(output: str) -> dict:
    def number(key):
        m = re.search(r"(?m)^\s*" + key + r":\s*(\d+)\s*$", output)
        return int(m.group(1)) if m else None
    def string(key):
        m = re.search(r"(?m)^\s*" + key + r":\s*(\S+)\s*$", output)
        return m.group(1) if m else None
    return {"status": string("Status"), "launch_state": string("LaunchState"),
            "android_total_ms": number("TotalTime"), "android_wait_ms": number("WaitTime")}


def memory_metrics(output: str) -> dict:
    def number(pattern):
        m = re.search(pattern, output, re.M)
        return int(m.group(1)) if m else None
    pss = number(r"^\s*TOTAL PSS:\s*(\d+)\b")
    if pss is None:
        pss = number(r"^\s*TOTAL\s+(\d+)\s+\d+\s+\d+")
    return {"pss_kib": pss,
            "rss_kib": number(r"^\s*TOTAL RSS:\s*(\d+)\b"),
            "java_heap_pss_kib": number(r"^\s*Java Heap:\s*(\d+)\b"),
            "native_heap_pss_kib": number(r"^\s*Native Heap:\s*(\d+)\b"),
            "swap_pss_kib": number(r"^\s*TOTAL SWAP PSS:\s*(\d+)\b")}


def proc_stat(output: str, expected_pid: int):
    # comm may contain spaces or ')' so split after its last closing parenthesis.
    try:
        prefix, rest = output.strip().rsplit(")", 1)
        if int(prefix.split("(", 1)[0].strip()) != expected_pid:
            return None
        fields = rest.split()
        # rest starts at stat field 3. Fields 14/15 are utime/stime; 22 is starttime.
        user, system, start = int(fields[11]), int(fields[12]), int(fields[19])
        if min(user, system, start) < 0:
            return None
        return user + system, start
    except (ValueError, IndexError):
        return None


def cpu_delta(first, second, elapsed: float, hz: int | None) -> dict:
    invalid = {"cpu_ticks": None, "cpu_ticks_per_second": None,
               "cpu_one_core_percent": None}
    if first is None or second is None or first[1] != second[1] or elapsed <= 0:
        return invalid
    ticks = second[0] - first[0]
    if ticks < 0:
        return invalid
    return {"cpu_ticks": ticks, "cpu_ticks_per_second": round(ticks / elapsed, 3),
            "cpu_one_core_percent": round(100 * ticks / hz / elapsed, 3)
            if hz is not None and hz > 0 else None}


def system_cpu(output: str, pid: int) -> float | None:
    # Android's own observation window, distinct from our monotonic idle window.
    m = re.search(r"(?m)^\s*[+]?(\d+(?:\.\d+)?)%\s+" + str(pid) +
                  "/" + re.escape(PACKAGE) + r":", output)
    return float(m.group(1)) if m else None


def positive_int(value: str | None) -> int | None:
    try:
        n = int((value or "").strip())
        return n if n > 0 else None
    except ValueError:
        return None


class PerformanceProfile:
    def __init__(self, adb, apk: Path, output: Path):
        self.adb = adb
        self.output = output
        self.raw = output / "performance-raw"
        self.raw.mkdir(parents=True, exist_ok=True)
        self.hz = positive_int(self.optional("shell", "getconf", "CLK_TCK"))
        digest = hashlib.sha256()
        with apk.open("rb") as handle:
            for block in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(block)
        revision = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True,
                                  text=True, timeout=10)
        self.report = {
            "format": 1, "revision": revision.stdout.strip() if revision.returncode == 0 else None,
            "apk_sha256": digest.hexdigest(), "package": PACKAGE, "build": "debug",
            "sdk": self.optional("shell", "getprop", "ro.build.version.sdk"),
            "abi": self.optional("shell", "getprop", "ro.product.cpu.abi"),
            "fingerprint": self.optional("shell", "getprop", "ro.build.fingerprint"),
            "screen": self.optional("shell", "wm", "size"),
            "clock_ticks_per_second": self.hz,
            "method": {"pss": "dumpsys meminfo PID, KiB; main process only",
                       "cpu": "run-as /proc/PID/stat, host monotonic interval; optional Android cpuinfo",
                       "navigation": "host ADB tap to first matching UI hierarchy, includes polling/ADB",
                       "startup": "am start -W after force-stop; existing data, not first install"},
            "startup": [], "screens": []}
        self.flush()

    def optional(self, *args):
        try:
            return self.adb(*args, timeout=15).strip()
        except (RuntimeError, subprocess.TimeoutExpired, OSError):
            return None

    def pid(self):
        value = self.optional("shell", "pidof", "-s", PACKAGE)
        return positive_int(value)

    def stat(self, pid):
        raw = self.optional("shell", "run-as", PACKAGE, "cat", f"/proc/{pid}/stat")
        return proc_stat(raw, pid) if raw is not None else None

    def flush(self):
        cold = [r["android_total_ms"] for r in self.report["startup"]
                if r["status"] == "ok" and r["launch_state"] in ("COLD", None)
                and r["android_total_ms"] is not None]
        self.report["cold_total_median_ms"] = statistics.median(cold) if cold else None
        self.report["cold_valid_samples"] = len(cold)
        target = self.output / "performance-profile.json"
        temp = target.with_suffix(".tmp")
        temp.write_text(json.dumps(self.report, ensure_ascii=False, indent=2), encoding="utf-8")
        temp.replace(target)

    def screen(self, name: str, tap_at: float | None = None, observed_at: float | None = None):
        # No navigation time unless the caller provides both actual observation timestamps.
        row = {"screen": name, "navigation_observed_ms":
               round((observed_at - tap_at) * 1000) if tap_at is not None and
               observed_at is not None and observed_at >= tap_at else None}
        time.sleep(1)
        pid = self.pid()
        row["pid"] = pid
        if pid is not None:
            first = self.stat(pid)
            begin = time.monotonic()
            time.sleep(5)
            second = self.stat(pid)
            elapsed = time.monotonic() - begin
            mem = self.optional("shell", "dumpsys", "meminfo", str(pid))
            cpu = self.optional("shell", "dumpsys", "cpuinfo")
            if self.pid() != pid:
                row["state"] = "process_changed"
            else:
                row.update(memory_metrics(mem or ""))
                row.update(cpu_delta(first, second, elapsed, self.hz))
                row["cpu_window_seconds"] = round(elapsed, 3)
                row["android_cpuinfo_percent"] = system_cpu(cpu or "", pid)
                row["state"] = "observed" if row["pss_kib"] is not None else "memory_unavailable"
                if mem is not None:
                    (self.raw / (name + "-meminfo.txt")).write_text(mem, encoding="utf-8")
        else:
            row["state"] = "pid_unavailable"
        self.report["screens"].append(row)
        self.flush()
        print("PERF:", json.dumps(row, ensure_ascii=False), flush=True)

    def cold_start(self, ready):
        # Only called from disposable emulator smoke after all user-triggered
        # recording/capture cases have completed. No pm clear or data deletion.
        for i in range(3):
            self.adb("shell", "am", "force-stop", PACKAGE, timeout=30)
            if self.pid() is not None:
                raise AssertionError("Cold-start process still alive after force-stop")
            begin = time.monotonic()
            raw = self.adb("shell", "am", "start", "-W", "-n",
                           PACKAGE + "/.NavigationHostActivity", timeout=60)
            row = startup_metrics(raw)
            row["attempt"] = i + 1
            (self.raw / f"cold-{i+1}.txt").write_text(raw, encoding="utf-8")
            if row["status"] != "ok":
                self.report["startup"].append(row)
                self.flush()
                raise AssertionError("Android cold start failed")
            ready()
            row["home_visible_observed_ms"] = round((time.monotonic() - begin) * 1000)
            self.report["startup"].append(row)
            self.flush()
            print("PERF startup:", json.dumps(row), flush=True)
