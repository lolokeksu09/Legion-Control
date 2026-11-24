# Alpha 51 development — Battery Lab 2.0

Base published Alpha 49 / 5b0b957. Independent branch from main so Alpha 50 network CI is not overwritten. Source-only, no version or signature changes.

BAT-01: visible-only, explicit 2/5/10-minute run, 5-second pause between reads, up to 121 samples, no wake lock/service. Stop on background, 45 °C battery, Android SEVERE+, or three read failures. No active stress workload.

BAT-02: actual Android BatteryManager values: current, voltage, SOC, battery temperature, source/status, remaining charge/energy counters and platform charge ETA when available. Estimate battery-side signed W from mV×µA/1e9; contradictory direction rejected, no invented OEM fields, charger input/efficiency/bypass/full capacity not inferred. Android API reference: https://developer.android.com/reference/android/os/BatteryManager

BAT-03: trapezoidal integration of eligible adjacent power samples into observed received/spent Wh; exclude >12s gaps, missing signals, source/status transitions and sign reversals. Show covered/observed time and exclusions, no full-window extrapolation. Charts for power, temperature, current, SOC.

BAT-04: separate private SQLite archive, manual save up to 30 runs, no eviction, deduplicate ID, reopen history/charts, confirmed one-record deletion, share summary using Android chooser. Compare distinct completed tests of same plan, same charge/discharge status, close initial SOC (5 pp)/temperature (3 °C), stable sources within each run and ≥80% covered time; differences descriptive only. No main SQLite v12 migration. Not part of full backup ZIP yet. CSV export freezes the selected report before opening Android SAF; no external write before confirmation. No automatic charging-cycle tracking or per-game attribution yet.

BAT-05: JVM tests added for units, signed energy, gaps, contradictory values, thermal stop and matched comparison. Exact CI and physical Lenovo checks pending. Two emulator instrumentation cases for archive capacity/deduplication/round-trip/delete and public battery reading are wired into the existing smoke script. Visual and physical acceptance pending. Battery Lab is inside existing Hardware Lab, no additional top-level navigation or permission.
