#!/usr/bin/env python3
"""Run all Android instrumentation and navigate the real build on a disposable emulator."""
from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from benchmark_home import benchmark_home, clock_ticks

PACKAGE = "dev.lolokeksu.legioncontrol"
EVIDENCE = Path("smoke-evidence")


def adb(*args: str, timeout: int = 45) -> str:
    result = subprocess.run(["adb", *args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=timeout)
    if result.returncode:
        raise RuntimeError((result.stdout + result.stderr)[-1500:])
    return result.stdout


def await_state(read, label: str, seconds: int = 30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = read()
        if value is not None:
            return value
        time.sleep(0.4)
    raise AssertionError(f"Android state not reached: {label}")


def find(resource_id: str):
    path = "/sdcard/legion-smoke.xml"
    adb("shell", "uiautomator", "dump", path, timeout=20)
    root = ET.fromstring(adb("shell", "cat", path, timeout=10))
    return next((item for item in root.iter("node")
                 if item.get("resource-id") == f"{PACKAGE}:id/{resource_id}"), None)


def tap(resource_id: str) -> None:
    node = await_state(lambda: find(resource_id), resource_id)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    if node.get("clickable") != "true" or x1 >= x2 or y1 >= y2:
        raise AssertionError(f"Control is not actionable: {resource_id}")
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def selected_tab(tab: str) -> None:
    def selection():
        node = find("hub_tab_" + tab)
        return node if node is not None and node.get("selected") == "true" else None
    await_state(selection, "selected tab: " + tab)


def resumed(activity: str) -> None:
    qualified = PACKAGE + "/." + activity
    full = PACKAGE + "/" + PACKAGE + "." + activity
    def top():
        result = adb("shell", "dumpsys", "activity", "activities", timeout=15)
        return next((line for line in result.splitlines()
                     if "topResumedActivity" in line and (qualified in line or full in line)), None)
    await_state(top, activity)


def screenshot(name: str) -> None:
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                          capture_output=True, check=True, timeout=20).stdout
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise AssertionError("Invalid screenshot")
    (EVIDENCE / (name + ".png")).write_bytes(data)


def instrumentation(test_apk: Path) -> int:
    adb("install", "-r", str(test_apk), timeout=120)
    raw = adb("shell", "am", "instrument", "-w",
              "-e", "package", PACKAGE,
              PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=420)
    (EVIDENCE / "instrumentation.txt").write_text(raw, encoding="utf-8")
    match = re.search(r"(?m)^OK\s*\(([1-9]\d*) tests?\)\s*$", raw)
    if not match or "FAILURES" in raw or "INSTRUMENTATION_FAILED" in raw:
        raise AssertionError("Instrumentation did not complete successfully: " + raw[-1800:])
    return int(match[1])


def main() -> None:
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("smoke-apk/app-debug.apk")
    test_apk = apk.with_name("app-debug-androidTest.apk")
    if not apk.is_file() or not test_apk.is_file():
        raise AssertionError("The matching app and instrumentation APKs are required")
    if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Smoke is restricted to a disposable emulator")
    EVIDENCE.mkdir(exist_ok=True)
    adb("install", "-r", str(apk), timeout=120)
    adb("shell", "pm", "clear", PACKAGE)
    count = instrumentation(test_apk)
    hz = clock_ticks(adb("shell", "run-as", PACKAGE, "cat", "cache/legion-ci-clock-ticks.txt"))
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.NavigationHostActivity")

    def home():
        resumed("LegionHubActivity")
        selected_tab("home")
        await_state(lambda: find("hub_center_device"), "ready Home dashboard")

    home()
    benchmark_home(adb, home, PACKAGE, apk, EVIDENCE, hz)
    home()
    screenshot("01-home")

    for tab in ("games", "tools", "history", "settings", "home"):
        tap("hub_tab_" + tab)
        selected_tab(tab)
        screenshot("02-tab-" + tab)

    tap("hub_center_games")
    selected_tab("games")
    tap("hub_tab_home")
    home()

    for center, screen in (
        ("device", "MainActivity"), ("peripherals", "MainActivity"),
        ("media", "CaptureStudioActivity"), ("analytics", "SessionLibraryActivity"),
    ):
        tap("hub_center_" + center)
        resumed(screen)
        screenshot("03-center-" + center)
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        home()

    services = adb("shell", "dumpsys", "activity", "services", PACKAGE)
    for service in ("CaptureRecordingService", "ReplayCaptureService",
                    "CaptureScreenshotService", "GameSessionRecorderService"):
        if service in services:
            raise AssertionError("Navigating started " + service)
    outcome = f"PASS: {count} Android tests, public launch, tabs, center routes and capture idle state."
    (EVIDENCE / "results.txt").write_text(outcome + "\n", encoding="utf-8")
    print(outcome, flush=True)


if __name__ == "__main__":
    main()
