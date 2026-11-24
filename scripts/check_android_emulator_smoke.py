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
GAMES_MARKER = "ИГРОВАЯ БИБЛИОТЕКА"
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

def expect_selected_tab(label: str) -> None:
    """Verify actual Android selected state, not just a visible tab label."""
    expected = "Открыть раздел " + label
    selected = [
        n for n in hierarchy().iter("node")
        if n.get("content-desc") == expected and n.get("selected") == "true"
    ]
    if len(selected) != 1:
        raise AssertionError(f"Bottom navigation does not select {label!r}")
    print("PASS active tab:", label, flush=True)




def actual_focused_input() -> str:
    """Read the committed EditText value, rather than trusting ADB's synthesized keys.

    Emulator input can reorder fast characters (alpha -> aplha); testing the
    intended command string would misdiagnose the app's SQLite persistence.
    """
    tree = hierarchy()
    fields = [node for node in tree.iter("node")
              if node.get("class") == "android.widget.EditText"]
    focused = [node.get("text", "") for node in fields
               if node.get("focused") == "true" and node.get("text")]
    if len(focused) == 1:
        value = focused[0]
    else:
        values = [node.get("text", "") for node in fields if node.get("text")]
        if len(values) != 1:
            raise AssertionError(f"Cannot identify marker input: {values!r}")
        value = values[0]
    if not re.fullmatch(r"[A-Za-z0-9_]{8,40}", value):
        raise AssertionError(f"Unexpected marker entered by ADB: {value!r}")
    print("PASS actual marker input:", value, flush=True)
    return value


def expect_scrolling(needle: str, seconds: int = 30, progressive: bool = False) -> None:
    width,height = dimensions()
    deadline=time.monotonic()+seconds
    step=0
    while time.monotonic() < deadline:
        if visible(needle):
            print("PASS report:",needle,flush=True)
            return
        # Progressive mode walks a long settings screen instead of bouncing
        # between the same two positions. Other report checks retain two-way
        # searching because their result might appear above the current viewport.
        y1,y2=(int(height*.78),int(height*.3)) if progressive else (
            (int(height*.3),int(height*.78)) if step%2==0 else
            (int(height*.78),int(height*.3))
        )
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


def find(needle: str, exact_only: bool = False) -> tuple[int, int] | None:
    tree = hierarchy()
    candidates = []
    for node in tree.iter("node"):
        label = node.get("text", "")
        desc = node.get("content-desc", "")
        bounds = node.get("bounds", "")
        exact = label == needle or desc == needle
        partial = needle.casefold() in (label + " " + desc).casefold()
        if not partial or (exact_only and not exact):
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if match:
            x1,y1,x2,y2 = map(int, match.groups())
            candidates.append((0 if exact else 1, (x1+x2)//2,(y1+y2)//2))
    if not candidates:
        return None
    candidates.sort()
    return candidates[0][1], candidates[0][2]


def click(needle: str, scrolls: int = 7, direction: str = "down",
          exact_only: bool = False) -> None:
    width, height = dimensions()
    for attempt in range(scrolls + 1):
        pos = find(needle, exact_only=exact_only)
        if pos:
            adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
            time.sleep(.75)
            print("TAP:", needle, flush=True)
            return
        if attempt != scrolls:
            if direction == "up":
                y1,y2=int(height*.31),int(height*.78)
            else:
                y1,y2=int(height*.78),int(height*.31)
            adb("shell", "input", "swipe", str(width//2), str(y1),
                str(width//2), str(y2), "370")
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
    # Instrumentation is run only on a disposable Google APIs emulator.
    # It checks real SQLite/SAF/MediaStore code paths and NEVER runs on user devices.
    test_apk = apk.with_name("app-debug-androidTest.apk")
    if not test_apk.is_file():
        raise AssertionError(f"Missing exact-build instrumentation APK: {test_apk}")
    adb("shell", "pm", "clear", PACKAGE, timeout=40)
    adb("install", "-r", str(test_apk), timeout=120)
    test_output = adb("shell", "am", "instrument", "-w",
        "-e", "class",
        "dev.lolokeksu.legioncontrol.FullBackupInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=190)
    if "OK (2 tests)" not in test_output or "FAILURES" in test_output:
        raise AssertionError("Backup restore instrumentation failed: " + test_output[-1400:])
    print("PASS: emulator-only SQLite backup/restore and corruption round-trip",flush=True)
    monitor_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".LiveMonitorInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (5 tests)" not in monitor_output or "FAILURES" in monitor_output:
        raise AssertionError("Live Monitor instrumentation failed: " + monitor_output[-1400:])
    print("PASS: actual monitor readings, baseline, foreground lifecycle and explicit restart", flush=True)
    automation_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".GameAutomationOwnershipInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (1 test)" not in automation_output or "FAILURES" in automation_output:
        raise AssertionError("Game Automation ownership instrumentation failed: " + automation_output[-1400:])
    print("PASS: automatic undo ownership and changed-profile rejection", flush=True)
    battery_lab_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".BatteryLabArchiveInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (2 tests)" not in battery_lab_output or "FAILURES" in battery_lab_output:
        raise AssertionError("Battery Lab instrumentation failed: " + battery_lab_output[-1400:])
    print("PASS: isolated battery archive and public Android battery reading", flush=True)
    compact_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".CompactDiagnosticsUiInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (2 tests)" not in compact_output or "FAILURES" in compact_output:
        raise AssertionError("Compact diagnostics layout instrumentation failed: " + compact_output[-1400:])
    print("PASS: responsive rows, touch size, click preservation and diagnostic chart visibility", flush=True)
    preflight_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".GamePreflightInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (1 test)" not in preflight_output or "FAILURES" in preflight_output:
        raise AssertionError("Game Preflight instrumentation failed: " + preflight_output[-1400:])
    print("PASS: actual per-game preflight report and isolated persistent presets", flush=True)
    compare_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".SessionCompareInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (1 test)" not in compare_output or "FAILURES" in compare_output:
        raise AssertionError("Session Compare instrumentation failed: " + compare_output[-1400:])
    print("PASS: emulator-only seeded SQLite sessions, equal windows, A/B swap and data isolation", flush=True)
    library_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".SessionLibraryInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (1 test)" not in library_output or "FAILURES" in library_output:
        raise AssertionError("Session Library instrumentation failed: " + library_output[-1400:])
    print("PASS: local library pagination, package/state isolation and selected session ZIP", flush=True)
    resource_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".ResourceOptimizationInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (2 tests)" not in resource_output or "FAILURES" in resource_output:
        raise AssertionError("Resource optimization instrumentation failed: " + resource_output[-1400:])
    print("PASS: real indexed lookup, profile count and bounded public launcher/Hub signals", flush=True)
    live_network_output = adb("shell", "am", "instrument", "-w", "-e", "class",
        PACKAGE + ".LiveNetworkArchiveInstrumentedTest",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
    if "OK (2 tests)" not in live_network_output or "FAILURES" in live_network_output:
        raise AssertionError("Live network archive instrumentation failed: " + live_network_output[-1400:])
    print("PASS: isolated live network archive round-trip, deduplication and explicit deletion", flush=True)
    adb("shell", "pm", "clear", PACKAGE, timeout=40)
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

    # Alpha.44: capture both affected pages before any long recorder backend
    # smoke. Their Activity facades are intentionally non-exported, so ADB
    # shell must NOT launch them directly. Use the actual user-visible Hub.
    click("Инструменты")
    expect(TOOLS_MARKER)
    click("Gaming Lab", scrolls=16)
    expect("LEGION CONTROL · GAMING LAB")
    expect("ПРОВЕРКИ УСТРОЙСТВА")
    expect("Главная")
    stage("00-alpha44-gaming-lab", "LEGION CONTROL · GAMING LAB")
    back_until(TOOLS_MARKER)
    click("Игры")
    expect(GAMES_MARKER)
    click("Game Session", scrolls=16)
    expect("LEGION · GAME SESSION")
    expect("ПОДГОТОВКА")
    expect("Главная")
    stage("00-alpha44-game-session", "LEGION · GAME SESSION")
    expect_selected_tab("Игры")
    # The recording CTA must be available without scrolling the initial page.
    expect("НАЧАТЬ ЗАПИСЬ", seconds=5)
    back_until(GAMES_MARKER)
    click("Главная", scrolls=0, exact_only=True)
    expect("Готов к запуску")
    click("Игры")
    stage("01-games-tab", "ИГРОВАЯ БИБЛИОТЕКА")
    expect("Управление библиотекой")
    click("Инструменты")
    expect(TOOLS_MARKER)
    capture("01-tools-tab")

    # Previously unobserved media and overlay screens: verify the real Activity
    # launches and core labels, without invoking any MediaProjection consent.
    click("Capture Studio")
    stage("01a-capture-studio", "CAPTURE STUDIO")
    expect("ЗАХВАТ И REPLAY")
    expect("Главная")
    expect_scrolling("INSTANT REPLAY", seconds=70, progressive=True)
    capture("01b-capture-studio-replay")
    back_until(TOOLS_MARKER)
    click("Игры")
    expect(GAMES_MARKER)
    click("Game Toolbox")
    stage("01c-game-toolbox", "GAME TOOLBOX")
    expect("ИГРОВАЯ ПАНЕЛЬ")  # Header owned by canonical NavigationHostActivity
    expect("Выбрать игру или приложение")
    expect("Главная")  # Persistent bottom navigation on formerly standalone screen
    back_until(GAMES_MARKER)

    click("Game Session")
    stage("02-game-session", "LEGION · GAME SESSION")
    back_until(GAMES_MARKER)

    click("Smart Profiles")
    stage("03-smart-profiles", "LEGION CONTROL · SMART PROFILES")
    expect_scrolling("Температура предупреждения, °C", seconds=20)
    expect_selected_tab("Игры")
    click("com.example.game", scrolls=12, direction="up")
    adb("shell", "input", "text", "com.android.settings")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")  # Hide IME, do not leave screen
    click("Открыть Game Session с этим профилем")
    expect("LEGION · GAME SESSION")
    expect("com.android.settings")
    capture("04-selected-package-preserved")

    # Recorder can only start from this explicit tap; network test remains unchecked.
    # Exact button text is required: the status paragraph also contains
    # «НАЧАТЬ ЗАПИСЬ» and a partial match taps a non-clickable TextView.
    click("НАЧАТЬ ЗАПИСЬ", exact_only=True)
    expect_scrolling("Запрошен запуск записи", seconds=17)
    expect_service(True)
    click("Добавить метку во время записи")
    expect("Отметить момент во время записи")
    adb("shell", "input", "text", "alpha30_marker")
    marker_that_android_received = actual_focused_input()
    click("Добавить", scrolls=0)
    expect_scrolling("Отправлена ручная метка", seconds=18)
    time.sleep(3)
    click("ОСТАНОВИТЬ ЗАПИСЬ", exact_only=True)
    expect_service(False)
    expect_scrolling("Отправлена команда остановки", seconds=17)
    time.sleep(2)
    click("Обновить и показать последние сессии")
    expect_scrolling("ПОСЛЕДНИЕ СЕССИИ", seconds=35)
    capture("05-recorded-session-history")
    click("Session Signals · хронология и события")
    stage("05b-session-signals", "LEGION · SESSION SIGNALS")
    expect_scrolling("USER_MARK", seconds=28)
    expect_scrolling(marker_that_android_received, seconds=28)
    capture("05c-manual-mark-in-timeline")
    click("Session Incidents · длительность состояний")
    stage("05d-session-incidents", "LEGION · SESSION INCIDENTS")
    expect_scrolling("ОТРЕЗКИ НАБЛЮДЕНИЙ", seconds=30)
    click("Фильтр категорий")
    expect("Выберите тип")
    click("Энергосбережение Android", scrolls=0)
    expect_scrolling("Фильтр: Энергосбережение Android", seconds=25)
    capture("05e-session-incidents-filter")
    back_until("LEGION · SESSION SIGNALS")
    back_until("LEGION · GAME SESSION")
    back_until("LEGION CONTROL · SMART PROFILES")
    back_until(GAMES_MARKER)
    click("Инструменты")
    expect(TOOLS_MARKER)

    click("Audio Lab")
    stage("06-audio-lab-idle", "AUDIO LAB")
    expect_selected_tab("Инструменты")
    back_until(TOOLS_MARKER)
    click("Haptic Lab")
    stage("07-haptic-lab-idle", "HAPTIC LAB")
    back_until(TOOLS_MARKER)

    # Controller live input keeps its Activity ownership, but the chrome
    # should match the Hub. Visit it after Haptic Lab while scrolled nearby.
    click("Gamepad Live")
    stage("01d-gamepad-live", "GAMEPAD LIVE")
    expect("ПРОВЕРКА ГЕЙМПАДА")
    expect("Главная")
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

    # Rust diagnostics is at the end of Services, so open it just before
    # switching to History rather than requiring an upward-only search.
    click("Диагностика Kotlin + Rust", scrolls=16)
    stage("01e-rust-diagnostics", "Диагностика устройства")
    expect("ДИАГНОСТИКА УСТРОЙСТВА")
    expect("Главная")
    back_until(TOOLS_MARKER)

    click("История")
    expect("ИСТОРИЯ И ДИАГНОСТИКА")
    capture("12-history-tab")
    click("Session Incidents")
    stage("12a-history-to-incidents", "LEGION · SESSION INCIDENTS")
    expect_selected_tab("История")
    back_until("ИСТОРИЯ И ДИАГНОСТИКА")
    click("Session Signals")
    stage("12b-history-to-signals", "LEGION · SESSION SIGNALS")
    back_until("ИСТОРИЯ И ДИАГНОСТИКА")
    click("Session Baselines")
    stage("12c-history-to-baselines", "LEGION · SESSION BASELINES")
    expect_scrolling("минимум 3", seconds=28)
    capture("12d-baseline-insufficient-history-honest")
    # Reports can be long. Return to the controls above the report and
    # explicitly select the session package: opening Signals requires it.
    click("Выбрать игру из локальной истории", scrolls=12, direction="up")
    expect("История игр")
    click("com.android.settings", scrolls=0)
    click("За последние 30 дней", scrolls=12, direction="up")
    expect_scrolling("Окно истории: 30 дней", seconds=28)
    click("Открыть Session Signals выбранной игры", scrolls=12, direction="up")
    stage("12e-baselines-to-signals", "LEGION · SESSION SIGNALS")
    back_until("LEGION · SESSION BASELINES")
    back_until("ИСТОРИЯ И ДИАГНОСТИКА")
    click("Настройки")
    expect(SETTINGS_MARKER)
    capture("13-settings-tab")
    click("Application Center")
    stage("13-application-center", "APPLICATION CONTROL CENTER")
    back_until(SETTINGS_MARKER)
    click("Главная")
    stage("14-return-home", "Готов к запуску")
    # Repeat UI traversal after the entire smoke to catch navigation and theme
    # regressions, without exporting previously private legacy Activities.
    click("Инструменты")
    expect(TOOLS_MARKER)
    click("Gaming Lab", scrolls=16)
    stage("15-gaming-after-smoke", "LEGION CONTROL · GAMING LAB")
    expect("ИГРОВАЯ ДИАГНОСТИКА")
    expect("Главная")
    back_until(TOOLS_MARKER)
    click("Игры")
    expect(GAMES_MARKER)
    click("Game Session", scrolls=16)
    stage("16-session-after-smoke", "LEGION · GAME SESSION")
    expect("ЗАПИСЬ ИГРОВОЙ СЕССИИ")
    expect("Главная")
    back_until(GAMES_MARKER)
    click("Главная")
    stage("17-return-home-after-legacy", "Готов к запуску")

    # Verify the new user-facing tool catalog rather than treating static source
    # strings as evidence that search and action routing actually work.
    click("Инструменты")
    expect(TOOLS_MARKER)
    click("Поиск по инструментам Legion Control", scrolls=0)
    adb("shell", "input", "text", "stereo")
    expect("Проверка стерео")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    click("Проверка стерео", scrolls=0)
    # The specific control must be visible without manual swiping or activation.
    expect("Стерео · 440 Гц")
    capture("18a-finder-stereo-anchor")
    back_until(TOOLS_MARKER)
    click("Поиск по инструментам Legion Control", scrolls=0)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    # Clear any restored search text, then select a standalone Activity target.
    adb("shell", "input", "keyevent", "--longpress", *["KEYCODE_DEL"] * 32)
    adb("shell", "input", "text", "replay")
    expect("Мгновенный повтор")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    click("Мгновенный повтор", scrolls=0)
    expect("ЗАПУСТИТЬ REPLAY-БУФЕР")
    capture("18b-finder-replay-anchor")
    if "ReplayCaptureService" in adb("shell", "dumpsys", "activity", "services", PACKAGE):
        raise AssertionError("Opening a finder result unexpectedly started Replay")
    back_until(TOOLS_MARKER)
    click("Поиск по инструментам Legion Control", scrolls=0)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keyevent", "--longpress", *["KEYCODE_DEL"] * 32)
    adb("shell", "input", "text", "audio")
    expect("Audio Lab")
    if visible("Gaming Lab"):
        raise AssertionError("Tool search left unrelated modules visible")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")  # Dismiss IME
    click("Audio Lab", scrolls=7, exact_only=True)
    stage("18-tools-search-opens-audio", "AUDIO LAB")
    back_until(TOOLS_MARKER)
    click("Поиск по инструментам Legion Control", scrolls=0)
    adb("shell", "input", "text", "unlikelysearch999")
    expect("Инструменты не найдены")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    click("Сбросить поиск", scrolls=0)
    expect("Capture Studio")
    click("Настройки")
    expect(SETTINGS_MARKER)
    expect("Поверх других окон")
    click("Резервная копия ZIP", scrolls=12)
    expect("ЭКСПОРТ ИСТОРИИ И НАСТРОЕК")
    capture("18c-settings-backup-anchor")
    back_until(SETTINGS_MARKER)
    click("Главная")
    stage("19-return-home-after-search", "Готов к запуску")
    click("Live Monitor", scrolls=10, exact_only=True)
    stage("20-live-monitor-idle", "ЖИВОЙ МОНИТОР УСТРОЙСТВА")
    expect("Показаний пока нет")
    click("НАЧАТЬ МОНИТОРИНГ", exact_only=True)
    expect(" / 120 замеров")
    click("ОСТАНОВИТЬ МОНИТОРИНГ", exact_only=True)
    expect("Мониторинг остановлен пользователем")
    capture("21-live-monitor-stopped")
    expect_scrolling("Доступная RAM:", seconds=20)
    click("ЗАФИКСИРОВАТЬ КОНТРОЛЬНУЮ ТОЧКУ", scrolls=10, exact_only=True)
    # The comparison is below the fold after the shared report surface changes.
    # Require the same actual report, but scroll towards it instead of waiting
    # on the preceding viewport. Pinning updates the status message as well.
    expect_scrolling("Изменение относительно сохранённого замера", seconds=35, progressive=True)
    capture("22-live-monitor-baseline")
    click("НАЧАТЬ МОНИТОРИНГ", scrolls=12, direction="up", exact_only=True)
    expect("Начать новый мониторинг?")
    capture("22a-shared-confirmation-dialog")
    click("Отмена", scrolls=0, exact_only=True)
    expect_scrolling("Контрольная точка сохранена.", seconds=25)
    expect_scrolling("Изменение относительно сохранённого замера", seconds=35, progressive=True)
    capture("22b-cancel-preserves-baseline")
    # The origin Home is scrolled down to the Live Monitor tile. Its title may
    # be offscreen or loading. Press Back once and wait for the fixed host header;
    # repeated Back presses on Home would exit to Android Launcher.
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    expect("GAME HUB ·")
    expect_selected_tab("Главная")
    expect_scrolling("Готов к запуску", seconds=25)
    click("Игры")
    click("Game Preflight", scrolls=8, exact_only=True)
    stage("23-game-preflight-idle", "ПРОВЕРКА ПЕРЕД ИГРОЙ")
    expect("ВЫБРАТЬ ИГРУ ДЛЯ ПРОВЕРКИ")
    expect("Игра не выбрана")
    back_until(GAMES_MARKER)
    click("История")
    click("Session Compare", scrolls=10, exact_only=True)
    stage("24-session-compare-idle", "СРАВНЕНИЕ ИГРОВЫХ СЕССИЙ")
    expect("ВЫБРАТЬ ИГРУ ИЗ ИСТОРИИ")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    expect("ИСТОРИЯ И ДИАГНОСТИКА")
    expect_selected_tab("История")
    click("Session Library", scrolls=10, direction="up", exact_only=True)
    stage("25-session-library", "БИБЛИОТЕКА ИГРОВЫХ СЕССИЙ")
    expect("Фильтр по игре")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    expect("ИСТОРИЯ И ДИАГНОСТИКА")
    expect_selected_tab("История")
    click("Главная")
    expect_scrolling("Готов к запуску", seconds=25)
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
