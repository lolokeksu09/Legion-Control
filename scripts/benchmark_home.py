#!/usr/bin/env python3
"""Report-only benchmark of the actual debug process on a disposable CI emulator.
No extra Android permission, runtime telemetry, data clear or synthetic timing."""
import hashlib
import json
import re
import statistics
import time
import unittest
from pathlib import Path


def launch_ms(raw):
    if not re.search(r"(?m)^Status:\s*ok\s*$", raw):
        raise ValueError("am start failed")
    m = re.search(r"(?m)^TotalTime:\s*(\d+)\s*$", raw)
    if not m or int(m[1]) <= 0:
        raise ValueError("No positive TotalTime")
    state = re.search(r"(?m)^LaunchState:\s*(\w+)", raw)
    if state and state[1].upper() != "COLD":
        raise ValueError("Not a cold process launch")
    return int(m[1])


def pss_kib(raw):
    m = re.search(r"TOTAL PSS:\s*(\d+)", raw)
    if not m:
        m = re.search(r"(?m)^\s*TOTAL\s+(\d+)\s+\d+", raw)
    if not m or int(m[1]) <= 0:
        raise ValueError("No positive total PSS")
    return int(m[1])


def clock_ticks(raw):
    value = raw.strip()
    if not re.fullmatch(r"[0-9]{1,6}", value) or not 1 <= int(value) <= 100000:
        raise ValueError("Invalid Android clock tick calibration")
    return int(value)


def proc_stat(raw):
    # comm can contain spaces or parentheses; remaining fields begin at state (3).
    fields = raw.strip().rsplit(")", 1)[1].split()
    return int(fields[11]) + int(fields[12]), int(fields[19])


def benchmark_home(adb, await_home, package, apk, evidence, ticks_per_second):
    report = {"schema": 1, "status": "incomplete", "environment": "CI debug emulator; process-cold, not storage-cold",
              "apk_sha256": hashlib.sha256(Path(apk).read_bytes()).hexdigest(),
              "samples": [], "errors": []}
    output = Path(evidence) / "performance-home.json"
    try:
        if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
            raise RuntimeError("Benchmark restricted to emulator")
        report["sdk"] = adb("shell", "getprop", "ro.build.version.sdk").strip()
        report["abi"] = adb("shell", "getprop", "ro.product.cpu.abi").strip()
        if not re.fullmatch(r"[a-zA-Z0-9_.]+", package):
            raise ValueError("Invalid benchmark package")
        if not isinstance(ticks_per_second, int) or ticks_per_second <= 0:
            raise ValueError("Missing device clock tick calibration")
        report["clock_ticks_per_second"] = ticks_per_second
        for _ in range(3):
            adb("shell", "am", "force-stop", package)
            # pidof legitimately returns exit 1 if the process does not exist.
            if adb("shell", "pidof " + package + " || true").strip():
                raise RuntimeError("Old process survived force-stop")
            start = time.monotonic()
            raw = adb("shell", "am", "start", "-W", "-n",
                      package + "/.NavigationHostActivity", timeout=60)
            await_home()
            sample = {"home_observed_ms": round((time.monotonic() - start) * 1000)}
            try:
                sample["launcher_wait_ms"] = launch_ms(raw)
            except ValueError:
                # The no-display launcher may finish before Android reports a draw.
                pass
            time.sleep(3)
            pid = adb("shell", "pidof", package).strip()
            if not pid.isdigit():
                raise ValueError("Single app process required")
            sample["pss_kib"] = pss_kib(adb("shell", "dumpsys", "meminfo", pid))
            try:
                def cpu():
                    t = time.monotonic()
                    ticks, start = proc_stat(adb("shell", "run-as", package, "cat", "/proc/" + pid + "/stat"))
                    return (t + time.monotonic()) / 2, ticks, start
                t0, a, started = cpu()
                time.sleep(5)
                t1, b, ended = cpu()
                if started != ended or b < a or t1 <= t0 or adb("shell", "pidof", package).strip() != pid:
                    raise ValueError("CPU process changed")
                sample["idle_cpu_ticks"] = b - a
                sample["idle_window_seconds"] = round(t1 - t0, 3)
                sample["idle_cpu_percent_one_core"] = round(100 * (b-a) / ticks_per_second / (t1-t0), 3)
            except Exception as error:
                sample["cpu_unavailable"] = type(error).__name__
            report["samples"].append(sample)
        report["median_home_observed_ms"] = statistics.median(s["home_observed_ms"] for s in report["samples"])
        report["median_pss_kib"] = statistics.median(s["pss_kib"] for s in report["samples"])
        cpu = [s["idle_cpu_percent_one_core"] for s in report["samples"] if "idle_cpu_percent_one_core" in s]
        if len(cpu) == 3:
            report["median_idle_cpu_percent_one_core"] = statistics.median(cpu)
        report["status"] = "complete" if len(cpu) == 3 else "partial_cpu_unavailable"
    except Exception as error:
        report["errors"].append(type(error).__name__ + ": " + str(error)[:250])
    finally:
        output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("PERFORMANCE:", json.dumps(report, ensure_ascii=False), flush=True)
    # No synthetic success or threshold for noisy hosted runners. Existing smoke
    # still requires Home and all functional gates after the report.
    return report


class ParserTests(unittest.TestCase):
    def test_cold_start(self):
        self.assertEqual(launch_ms("Status: ok\nLaunchState: COLD\nTotalTime: 456\n"), 456)
        for value in ("Status: timeout\nTotalTime: 1", "Status: ok\nLaunchState: HOT\nTotalTime: 5", "Status: ok\n"):
            with self.assertRaises(ValueError):
                launch_ms(value)

    def test_pss(self):
        self.assertEqual(pss_kib(" TOTAL PSS: 12345 TOTAL RSS: 45678"), 12345)
        self.assertEqual(pss_kib("  TOTAL 3456 100 99"), 3456)
        with self.assertRaises(ValueError):
            pss_kib("TOTAL RSS: 1234")

    def test_clock_ticks(self):
        self.assertEqual(clock_ticks("100\r\n"), 100)
        for raw in ("", "permission denied", "0", "100001", "100 200"):
            with self.assertRaises(ValueError):
                clock_ticks(raw)

    def test_proc_stat(self):
        fields = ["S"] + ["0"] * 20
        fields[11], fields[12], fields[19] = "22", "7", "1000"
        self.assertEqual(proc_stat("42 (name ) with spaces) " + " ".join(fields)), (29, 1000))


if __name__ == "__main__":
    unittest.main()
