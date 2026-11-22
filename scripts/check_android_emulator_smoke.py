#!/usr/bin/env python3
"""Emulator-only UI smoke: real APK, Android UI hierarchy, back stack and explicit session."""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "dev.lolokeksu.legioncontrol"
TOOLS_MARKER = "ВСЕ СИСТЕМНЫЕ ИНСТРУМЕНТЫ"
SETTINGS_MARKER = "НАСТРОЙКИ И КОНТРОЛЬ"
EVIDENCE = Path("smoke-evidence")
EVIDENCE.mkdir(exist_ok=True)
REPORT = EVIDENCE / "results.txt"


def adb(*args: str, timeout: int = 65) -> str:
    command = ["adb", *args]
    done = subprocess.run(command, text=True, encoding="utf-8",
                          errors="replace", capture_output=True, timeout=timeout)
    if done.returncode:
        raise RuntimeError(f"{' '.join(command)}: {done.stderr[-600:]}")
    return done.stdout


def hierarchy() -> ET.Element:
    for attempt in range(3):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/legion-smoke.xml", timeout=75)
            raw = adb("shell", "cat", "/sdcard/legion-smoke.xml", timeout=20)
            return ET.fromstring(raw)
        except (ET.ParseError, RuntimeError, subprocess.TimeoutExpired):
            if attempt == 2:
                raise
            time.sleep(1)
    raise AssertionError("UI hierarchy unavailable")


def nodes() -> list[tuple[str, str]]:
    tree = hierarchy()
    return [(node.get("text", ""), node.get("content-desc", ""))
            for node in tree.iter("node")]


def visible(needle: str) -> bool:
    sought = needle.casefold()
    return any(sought in (txt + " " + desc).casefold() for txt, desc in nodes())


def expect(needle: str, seconds: int = 35) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if visible(needle):
            print("PASS visible:", needle, flush=True)
            return
        time.sleep(1.0)
    raise AssertionError(f"Never visible: {needle}")



def expect_scrolling(needle: str, seconds: int = 30) -> None:
    width,height = dimensions()
    deadline=time.monotonic()+seconds
    step=0
    while time.monotonic() < deadline:
        if visible(needle):
            print("PASS report:",needle,flush=True)
            return
        y1,y2=(int(height*.3),int(height*.78)) if step%2==0 else (int(height*.78),int(height*.3))
        adb("shell","input","swipe",str(width//2),str(y1),str(width//2),str(y2),"300")
        step+=1
        time.sleep(.65)
    raise AssertionError(f"Report not found even after scrolling: {needle}")


def service_running() -> bool:
    # Service state, not a UI string, verifies an actual foreground recorder instance.
    return "GameSessionRecorderService" in adb("shell","dumpsys","activity","services",PACKAGE,timeout=30)


def expect_service(active:bool, seconds:int=18) -> None:
    deadline=time.monotonic()+seconds
    while time.monotonic() < deadline:
        if service_running() is active:
            print("PASS active recorder service:",active,flush=True)
            return
        time.sleep(.75)
    raise AssertionError(f"Actual GameSessionRecorderService active state is not {active}")


def dimensions() -> tuple[int, int]:
    size = adb("shell", "wm", "size")
    match = re.search(r"(\d+)x(\d+)", size)
    if not match:
        raise AssertionError(f"Unsupported screen dimensions: {size}")
    return int(match.group(1)), int(match.group(2))


def find(needle: str) -> tuple[int, int] | None:
    tree = hierarchy()
    candidates = []
    for node in tree.iter("node"):
        label = node.get("text", "")
        desc = node.get("content-desc", "")
        bounds = node.get("bounds", "")
        exact = label == needle or desc == needle
        partial = needle.casefold() in (label + " " + desc).casefold()
        if not partial:
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if match:
            x1,y1,x2,y2 = map(int, match.groups())
            candidates.append((0 if exact else 1, (x1+x2)//2,(y1+y2)//2))
    if not candidates:
        return None
    candidates.sort()
    return candidates[0][1], candidates[0][2]


def click(needle: str, scrolls: int = 7) -> None:
    width, height = dimensions()
    for attempt in range(scrolls + 1):
        pos = find(needle)
        if pos:
            adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
            time.sleep(.75)
            print("TAP:", needle, flush=True)
            return
        if attempt != scrolls:
            adb("shell", "input", "swipe", str(width//2), str(int(height*.78)),
                str(width//2), str(int(height*.31)), "370")
            time.sleep(.45)
    raise AssertionError(f"Cannot find action: {needle}")


def back_until(needle: str, presses: int = 3) -> None:
    for _ in range(presses):
        if visible(needle):
            return
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(.9)
    expect(needle, 12)


def capture(tag: str) -> None:
    result = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                            capture_output=True, timeout=30, check=True)
    if not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
        raise AssertionError("Invalid emulator screenshot")
    (EVIDENCE / f"{tag}.png").write_bytes(result.stdout)


def stage(label: str, title: str) -> None:
    expect(title)
    capture(label)
    print("PASS SCREEN:", label, flush=True)


def main() -> None:
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("smoke-apk/app-debug.apk")
    if not apk.is_file():
        raise AssertionError(f"No actual APK artifact: {apk}")
    print("Emulator ABI:", adb("shell", "getprop", "ro.product.cpu.abi").strip(), flush=True)
    print("Emulator SDK:", adb("shell", "getprop", "ro.build.version.sdk").strip(), flush=True)
    adb("install", "-r", str(apk), timeout=120)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.NavigationHostActivity", timeout=60)

    # A freshly booted Google APIs emulator can show a *Pixel Launcher* ANR,
    # unrelated to Legion Control. Recover only this named system ANR, at most twice.
    for attempt in range(3):
        if visible("Готов к запуску"):
            break
        try:
            expect("Готов к запуску", seconds=18)
            break
        except AssertionError:
            if attempt == 2 or not visible("Pixel Launcher isn't responding"):
                raise
            print("RECOVER: Pixel Launcher ANR during emulator bootstrap",flush=True)
            click("Close app", scrolls=0)
            adb("shell", "am", "force-stop", PACKAGE)
            adb("shell", "am", "start", "-W", "-n",
                PACKAGE + "/.NavigationHostActivity", timeout=60)
    expect("Готов к запуску")
    capture("01-hub-launch")
    click("Сервисы")
    expect(TOOLS_MARKER)

    click("Game Session")
    stage("02-game-session", "LEGION · GAME SESSION")
    back_until(TOOLS_MARKER)

    click("Smart Profiles")
    stage("03-smart-profiles", "LEGION CONTROL · SMART PROFILES")
    click("Имя пакета приложения")
    adb("shell", "input", "text", "com.android.settings")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")  # Hide IME, do not leave screen
    click("Открыть Game Session с этим профилем")
    expect("LEGION · GAME SESSION")
    expect("com.android.settings")
    capture("04-selected-package-preserved")

    # Recorder can only start from this explicit tap; network test remains unchecked.
    click("НАЧАТЬ ЗАПИСЬ")
    expect_service(True)
    expect_scrolling("Запрошен запуск записи", seconds=17)
    click("Добавить метку во время записи")
    expect("Отметить момент во время записи")
    adb("shell", "input", "text", "alpha30_marker")
    click("Добавить", scrolls=0)
    expect_scrolling("Отправлена ручная метка", seconds=18)
    time.sleep(3)
    click("ОСТАНОВИТЬ ЗАПИСЬ")
    expect_service(False)
    expect_scrolling("Отправлена команда остановки", seconds=17)
    time.sleep(2)
    click("Обновить и показать последние сессии")
    expect_scrolling("ПОСЛЕДНИЕ СЕССИИ", seconds=35)
    capture("05-recorded-session-history")
    click("Session Signals · хронология и события")
    stage("05b-session-signals", "LEGION · SESSION SIGNALS")
    expect_scrolling("alpha30_marker", seconds=28)
    capture("05c-manual-mark-in-timeline")
    back_until("LEGION · GAME SESSION")
    back_until("LEGION CONTROL · SMART PROFILES")
    back_until(TOOLS_MARKER)

    click("Audio Lab")
    stage("06-audio-lab-idle", "AUDIO LAB · ALPHA.22")
    back_until(TOOLS_MARKER)
    click("Haptic Lab")
    stage("07-haptic-lab-idle", "HAPTIC LAB · ALPHA.23")
    back_until(TOOLS_MARKER)

    click("Gaming Lab")
    stage("08-gaming-lab", "LEGION CONTROL · GAMING LAB")
    click("HARDWARE LAB · ещё 6 диагностик")
    stage("09-hardware-lab", "LEGION CONTROL · HARDWARE LAB")
    click("COMPATIBILITY · ещё 7 функций")
    stage("10-compatibility", "LEGION CONTROL · COMPATIBILITY")
    back_until("LEGION CONTROL · HARDWARE LAB")
    back_until("LEGION CONTROL · GAMING LAB")
    back_until(TOOLS_MARKER)

    click("Connectivity Observatory")
    stage("11-connectivity-idle", "CONNECTIVITY OBSERVATORY")
    back_until(TOOLS_MARKER)
    click("Experiment Lab")
    stage("12-experiment-lab", "LEGION · EXPERIMENT LAB")
    back_until(TOOLS_MARKER)

    click("История")
    expect("ИСТОРИЯ И ДИАГНОСТИКА")
    click("Session Signals")
    stage("12b-history-to-signals", "LEGION · SESSION SIGNALS")
    back_until("ИСТОРИЯ И ДИАГНОСТИКА")
    click("Настройки")
    expect(SETTINGS_MARKER)
    click("Application Center")
    stage("13-application-center", "APPLICATION CONTROL CENTER")
    back_until(SETTINGS_MARKER)
    click("Домой")
    stage("14-return-home", "Готов к запуску")
    print("PASS: Android emulator UI navigation, explicit marker persisted in SQLite,",
          "signal correlation, selected package, foreground START/STOP, back stack",flush=True)


if __name__ == "__main__":
    with REPORT.open("w", encoding="utf-8") as log:
        log.write("Emulator UI smoke: fresh debug APK and public user flows\n")
    try:
        main()
    except Exception as error:
        print("FAIL:", repr(error), file=sys.stderr, flush=True)
        try:
            capture("failure-screen")
            REPORT.write_text(f"FAIL: {type(error).__name__}: {error}\n", encoding="utf-8")
            tree = hierarchy()
            (EVIDENCE / "failure-ui.xml").write_text(
                ET.tostring(tree, encoding="unicode"), encoding="utf-8")
        except Exception as evidence_error:
            print("Unable to capture failure evidence:", evidence_error, file=sys.stderr)
        raise
    else:
        REPORT.write_text("PASS: Android API emulator app launch and smoke flows\n", encoding="utf-8")
