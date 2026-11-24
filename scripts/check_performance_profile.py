#!/usr/bin/env python3
"""Parser regression fixtures only; real measurements are produced by emulator smoke."""
from performance_profile import startup_metrics, memory_metrics, proc_stat, cpu_delta, system_cpu, positive_int

assert startup_metrics("Status: ok\nLaunchState: COLD\nTotalTime: 421\nWaitTime: 430\n") == {
    "status":"ok", "launch_state":"COLD", "android_total_ms":421, "android_wait_ms":430}
assert startup_metrics("Status: ok\n")["android_total_ms"] is None
assert memory_metrics(" Java Heap: 123\n Native Heap: 456\n TOTAL PSS: 789 TOTAL RSS: 999 TOTAL SWAP PSS: 12\n") == {"pss_kib":789,"rss_kib":999,"java_heap_pss_kib":123,"native_heap_pss_kib":456,"swap_pss_kib":12}
assert memory_metrics(" TOTAL  1024  100  200  0\n")["pss_kib"] == 1024
assert memory_metrics("unavailable")["pss_kib"] is None
assert memory_metrics(" TOTAL PSS: 0\n")["pss_kib"] == 0
fields = ["S"] + ["0"] * 19
fields[11], fields[12], fields[19] = "100", "20", "600"
line = "42 (game ) name) " + " ".join(fields)
assert proc_stat(line, 42) == (120, 600)
assert proc_stat(line, 43) is None and proc_stat("bad", 42) is None
assert cpu_delta((120,600),(170,600),5,100)["cpu_one_core_percent"] == 10.0
assert cpu_delta((120,600),(120,600),5,100)["cpu_one_core_percent"] == 0
assert cpu_delta((120,600),(170,600),5,None)["cpu_one_core_percent"] is None
assert cpu_delta((120,600),(170,600),5,None)["cpu_ticks_per_second"] == 10
assert cpu_delta((120,600),(170,601),5,100)["cpu_ticks"] is None
assert cpu_delta((120,600),(110,600),5,100)["cpu_ticks"] is None
assert cpu_delta((120,600),(170,600),0,100)["cpu_ticks"] is None
assert system_cpu("  2.3% 42/dev.lolokeksu.legioncontrol: 1% user",42) == 2.3
assert system_cpu("  2.3% 42/dev.lolokeksu.legioncontrol:service: 1% user",42) is None
assert positive_int("100") == 100 and positive_int("0") is None and positive_int(None) is None
print("PASS: performance parser fixtures, process identity, missing counters and CPU units")
