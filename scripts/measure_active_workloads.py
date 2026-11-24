#!/usr/bin/env python3
"""Paired main-process workload observations on a disposable CI emulator only."""
from __future__ import annotations
import json
import os
import statistics
import sys
import time
from pathlib import Path

from check_android_emulator_smoke import (
    adb, click, expect, expect_scrolling, visible, find, capture, hierarchy,
    expect_service, service_running, GAMES_MARKER, TOOLS_MARKER, EVIDENCE, PACKAGE,
)
from performance_profile import PerformanceProfile
from active_workload_state import visible_overlay
import xml.etree.ElementTree as ET

SOURCE_SHA = "a02654b523e4428a03c823518e7f3beb1c41fb63"
SOURCE_RUN = 45405804990


def service(name):
    return name in adb("shell", "dumpsys", "activity", "services", PACKAGE)


def until(predicate, name, seconds=25):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if predicate():
            return
        time.sleep(.5)
    raise AssertionError(name)


def has_button(label):
    return any(n.get("text") == label and n.get("enabled") == "true"
               and n.get("clickable") == "true" for n in hierarchy().iter("node"))


def summary(rows):
    # Report medians only across three valid samples; no zero substitution.
    result = {"valid_samples": len(rows)}
    for key in ("pss_kib", "rss_kib", "cpu_one_core_percent", "cpu_ticks_per_second"):
        values = [r.get(key) for r in rows]
        result[key] = statistics.median(values) if len(values) == 3 and all(
            isinstance(v, (int, float)) for v in values) else None
    return result


def compare(idle, active):
    a, b = summary(idle), summary(active)
    # Do not compare across a process restart.
    ids = {r.get("pid") for r in idle+active}
    delta = {k: round(b[k]-a[k], 3) if len(ids)==1 and None not in ids
             and a[k] is not None and b[k] is not None else None
             for k in ("pss_kib", "rss_kib", "cpu_one_core_percent", "cpu_ticks_per_second")}
    return {"idle":a, "active":b, "active_minus_idle":delta,
            "same_pid":len(ids)==1 and None not in ids}


def observe(perf, scenario, phase, guard):
    rows = []
    for i in range(3):
        if not guard():
            raise AssertionError(scenario + ": workload state absent before measurement")
        perf.screen(f"{scenario}-{phase}-{i+1}")
        row = perf.report["screens"][-1]
        # Endpoint checks cannot prove uninterrupted work between checks;
        # publish this limitation rather than treating them as a continuous trace.
        valid = guard() and row.get("state") == "observed"
        row.update(workload=scenario, phase=phase, state_verified_at_endpoints=valid)
        perf.flush()
        if not valid:
            raise AssertionError(scenario + ": workload or process state lost during measurement")
        rows.append(row)
    return rows


def launch_home():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE+"/.NavigationHostActivity")
    expect("Готов к запуску")


def open_tool(title):
    click("Инструменты", scrolls=0)
    expect(TOOLS_MARKER)
    click(title, scrolls=16)


def open_game(title):
    click("Игры", scrolls=0)
    expect(GAMES_MARKER)
    click(title, scrolls=16)


def session(perf):
    open_game("Smart Profiles")
    expect("LEGION CONTROL · SMART PROFILES")
    click("com.example.game", scrolls=12, direction="up")
    adb("shell","input","text","com.android.settings")
    adb("shell","input","keyevent","KEYCODE_BACK")
    click("Открыть Game Session с этим профилем")
    expect("LEGION · GAME SESSION")
    expect("com.android.settings")
    idle = observe(perf,"session-recorder","idle",lambda:not service_running())
    click("НАЧАТЬ ЗАПИСЬ",exact_only=True)
    expect_scrolling("Запрошен запуск записи",seconds=17)
    expect_service(True)
    active = observe(perf,"session-recorder","active",service_running)
    click("ОСТАНОВИТЬ ЗАПИСЬ",exact_only=True)
    expect_service(False)
    return compare(idle,active)


def network(perf):
    open_tool("Connectivity Observatory")
    expect("CONNECTIVITY OBSERVATORY")
    click("Стандарт · 20 проб",scrolls=16)
    click("Длительный · 40 проб",exact_only=True,scrolls=0)
    # Position the test control without starting work before baseline.
    expect_scrolling("Запустить тест",seconds=30,progressive=True)
    idle = observe(perf,"live-network","idle",lambda:has_button("Запустить тест"))
    click("Запустить тест",exact_only=True)
    expect("Остановить тест")
    expect("Проба ",seconds=30)
    active=observe(perf,"live-network","active",lambda:has_button("Остановить тест"))
    click("Остановить тест",exact_only=True)
    expect("Запустить тест")
    return compare(idle,active)


def overlay(perf):
    # Grant ONLY the existing overlay app-op to this app on this disposable CI
    # emulator. No root, no game profile or other system settings are changed.
    adb("shell","appops","set",PACKAGE,"SYSTEM_ALERT_WINDOW","allow")
    try:
        open_game("Game Toolbox")
        expect("GAME TOOLBOX")
        expect("Показ поверх окон: РАЗРЕШЁН")
        idle=observe(perf,"toolbox-overlay","idle",lambda:not service("GameToolboxService"))
        click("Включить плавающую панель",scrolls=16)
        until(lambda:service("GameToolboxService"),"Overlay service did not start")
        def shown():
            windows=adb("shell","dumpsys","window","windows")
            (EVIDENCE/"toolbox-window-state.txt").write_text(windows,encoding="utf-8")
            return service("GameToolboxService") and visible_overlay(windows,PACKAGE)
        until(shown,"No visible owned APPLICATION_OVERLAY window")
        capture("toolbox-active-before")
        active=observe(perf,"toolbox-overlay","active",shown)
        capture("toolbox-active-after")
        click("Остановить плавающую панель",scrolls=12)
        until(lambda:not service("GameToolboxService"),"Overlay service did not stop")
        return compare(idle,active)
    finally:
        adb("shell","appops","set",PACKAGE,"SYSTEM_ALERT_WINDOW","default")


def recording(perf):
    open_tool("Capture Studio")
    expect("CAPTURE STUDIO")
    idle=observe(perf,"screen-capture","idle",lambda:not service("CaptureRecordingService"))
    click("НАЧАТЬ ЗАПИСЬ ЭКРАНА",direction="up",scrolls=16,exact_only=True)
    # Explicit consent on disposable emulator only. Match the observed Android
    # 35 dialog title and SystemUI nodes; never click a generic Start in the app.
    expect("Start recording or casting with Legion Control?",seconds=20)
    capture("capture-consent")
    def system_label(labels):
        for node in hierarchy().iter("node"):
            if node.get("package")=="com.android.systemui" and node.get("enabled","true")=="true":
                for label in labels:
                    if label in (node.get("text"),node.get("content-desc")):
                        return label
        return None
    def system_click(labels):
        label=system_label(labels)
        if label is None:
            raise AssertionError("Unrecognized SystemUI control: "+str(labels))
        click(label,scrolls=0,exact_only=True)
    if system_label(("A single app","Share one app")):
        system_click(("A single app","Share one app"))
        expect("Entire screen",seconds=12)
        system_click(("Entire screen","Share entire screen","Full screen"))
    if not system_label(("Entire screen","Share entire screen","Full screen")):
        raise AssertionError("Full-screen consent selection not verified")
    capture("capture-consent-full-screen")
    system_click(("Start","Start now","Share screen"))
    def capturing():
        return service("CaptureRecordingService") and PACKAGE in adb("shell","dumpsys","media_projection")
    until(capturing,"No active MediaProjection",seconds=30)
    click("Обновить состояние записи",scrolls=20)
    expect_scrolling("Запись началась",seconds=30)
    active=observe(perf,"screen-capture","active",capturing)
    click("ОСТАНОВИТЬ ЗАПИСЬ",scrolls=20,direction="up",exact_only=True)
    until(lambda:not service("CaptureRecordingService"),"Capture did not stop",seconds=35)
    click("Обновить состояние записи",scrolls=20)
    expect_scrolling("MP4 сохранён",seconds=30)
    return compare(idle,active)


def main():
    if os.getenv("CI")!="true" or adb("shell","getprop","ro.kernel.qemu").strip()!="1":
        raise SystemExit("Only disposable GitHub CI Android emulator is supported")
    apk=Path(sys.argv[1])
    adb("install","-r",str(apk),timeout=120)
    perf=PerformanceProfile(adb,apk,EVIDENCE)
    perf.report["measurement_code_revision"]=perf.report["revision"]
    perf.report["revision"]=SOURCE_SHA
    perf.report["source_ci_run"]=SOURCE_RUN
    perf.report["method"]["workload"]="three paired idle/active windows, 5s CPU each; guard before and after; debug emulator, no game workload"
    report={"source_revision":SOURCE_SHA,"source_ci_run":SOURCE_RUN,
            "apk_sha256":perf.report["apk_sha256"],"scenarios":{}}
    scenarios={"session-recorder":session,"live-network":network,
               "toolbox-overlay":overlay,"screen-capture":recording}
    requested=sys.argv[2:] or list(scenarios)
    if len(set(requested))!=len(requested) or any(name not in scenarios for name in requested):
        raise SystemExit("Invalid workload selection")
    report["requested_scenarios"]=requested
    for name in requested:
        run=scenarios[name]
        try:
            launch_home()
            data=run(perf)
            report["scenarios"][name]={"status":"observed",**data}
            print("ACTIVE PERF:",name,json.dumps(data),flush=True)
        except Exception as e:
            report["scenarios"][name]={"status":"not_completed","reason":str(e)[:250]}
            print("ACTIVE PERF incomplete:",name,str(e),flush=True)
            try:
                capture("workload-"+name+"-failure")
                (EVIDENCE/("workload-"+name+"-failure.xml")).write_text(ET.tostring(hierarchy(),encoding="unicode"),encoding="utf-8")
            except Exception: pass
        finally:
            # Independent scenarios; force-stop ONLY our app in CI, not a user's
            # phone. No clearing data, deleting recordings or forcing consent.
            adb("shell","am","force-stop",PACKAGE)
            (EVIDENCE/"active-workloads.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
            perf.flush()
    if any(x["status"]!="observed" for x in report["scenarios"].values()):
        raise AssertionError("One or more workload cases not completed; inspect individual results")


if __name__=="__main__":
    main()
