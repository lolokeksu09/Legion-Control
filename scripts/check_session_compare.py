#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
model = (src / "SessionCompareAnalysis.kt").read_text()
vm = (src / "SessionLibraryViewModel.kt").read_text()
ui = (src / "SessionLibraryActivity.kt").read_text()
chart = (src / "SessionCompareChart.kt").read_text()
a = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml")
activity = manifest.find("./application/activity[@"+a+"name='.SessionLibraryActivity']")
assert activity is not None and activity.get(a+"exported") == "false"
assert "windows=listOf(5,10,15,30)" in model and "MAX_POINTS=2000" in model
assert "coverage<0.70" in model and "gaps>0" in model
assert "a.header.packageName==b.header.packageName" in model
assert "a.header.id!=b.header.id" in model
assert "comparable && first.sufficient && second.sufficient" in model
assert "repo.recentForPackage(row.packageName, 25)" in vm and "repo.points(header.id,SessionCompareAnalysis.MAX_POINTS+1)" in vm
assert "pair=null" in vm and "header.packageName==snapshot.detail.header.packageName" in vm
assert "point.timeMs-prev.timeMs in 1..45000L" in chart
assert "ActivityResultContracts.CreateDocument" in ui and "CsvEscaper.cell" in model
for banned in ("startForegroundService(", "startService(", "Settings.System.put", "repo.start(", "repo.record(", "repo.finish(", "deleteDatabase(", "HttpURLConnection"):
    assert banned not in model+vm+ui+chart, banned
print("PASS: read-only bounded same-game elapsed windows, conservative deltas, common chart axes and explicit export")
