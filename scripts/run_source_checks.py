#!/usr/bin/env python3
"""Canonical fail-fast source/SQLite contract suite for debug and signed CI."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CHECKS = (
    "check_workflow_integration.py",
    "check_backend.py",
    "check_release_signing.py",
    "check_session_backend.py",
    "check_persistent_history.py",
    "check_resource_telemetry.py",
    "check_diagnostics_integration.py",
    "check_thermal_battery_trends.py",
    "check_diagnostic_sqlite_migration.py",
    "check_extended_diagnostics_csv.py",
    "check_battery_health_backend.py",
    "check_sensor_sqlite_migration.py",
    "check_alpha11_diagnostics.py",
    "check_alpha12_integration.py",
    "check_alpha13_timed_battery.py",
    "check_alpha14_gaming.py",
    "check_alpha15_hardware.py",
    "check_alpha16_compatibility.py",
    "check_alpha17_sessions.py",
    "check_alpha18_smart_profiles.py",
    "check_alpha19_experiments.py",
    "check_alpha20_connectivity.py",
    "check_alpha21_application_center.py",
    "check_alpha22_audio_lab.py",
    "check_alpha23_haptic_lab.py",
    "check_alpha24_game_toolbox.py",
    "check_alpha25_capture_studio.py",
    "check_alpha26_mvvm_navigation.py",
    "check_alpha27_mvvm_backend.py",
    "check_alpha28_mvvm_backends.py",
    "check_alpha29_navigation_routes.py",
    "check_alpha30_session_signals.py",
    "check_alpha31_session_baselines.py",
    "check_alpha32_session_incidents.py",
    "check_alpha33_game_launch_profiles.py",
    "check_alpha34_screenshots.py",
    "check_alpha35_capture_pause_resume.py",
    "check_alpha36_playback_audio.py",
    "check_alpha37_clip_workshop.py",
    "check_alpha38_game_capture_profiles.py",
    "check_alpha39_replay.py",
    "check_alpha40_audit_remediation.py",
    "check_alpha41_replay_timestamps.py",
    "check_alpha42_toolbox_ui.py",
    "check_unified_amoled_frontend.py",
    "check_alpha44_module_ui.py",
    "check_amoled_design_system_audit.py",
    "check_feature_finder.py",
    "check_live_monitor.py",
    "check_game_preflight.py",
    "check_session_compare.py",
    "check_session_library.py",
    "check_runtime_sbom.py",
)

def main():
    if len(CHECKS) != len(set(CHECKS)):
        raise SystemExit("Duplicate source gate")
    subprocess.run([sys.executable, "workflow-standard/tools/check_standard.py",
                    "workflow-standard"], cwd=ROOT, check=True)
    for number, name in enumerate(CHECKS, 1):
        print(f"[{number}/{len(CHECKS)}] {name}", flush=True)
        subprocess.run([sys.executable, str(ROOT / "scripts" / name)], cwd=ROOT, check=True)
    print(f"PASS: {len(CHECKS)}/{len(CHECKS)} source and SQLite contracts", flush=True)

if __name__ == "__main__":
    main()
