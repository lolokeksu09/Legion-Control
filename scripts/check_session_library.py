#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
model = (src / "SessionLibraryModels.kt").read_text()
repo = (src / "GameSessionRepository.kt").read_text()
ui = (src / "SessionLibraryActivity.kt").read_text()
vm = (src / "SessionLibraryViewModel.kt").read_text()
export = (src / "SessionLibraryExport.kt").read_text()
a = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml")
activity = manifest.find("./application/activity[@"+a+"name='.SessionLibraryActivity']")
assert activity is not None and activity.get(a+"exported") == "false"
assert "PAGE_SIZE=20" in model and "package_name=?" in model and "session_id<?" in model
assert "ORDER BY started_at_ms DESC,session_id DESC LIMIT ?" in repo
assert "query.args+" in repo and "require(h.endedAtMs!=null)" in repo
assert "s.detail!==detail" in vm and "detail.header.id==row.id" in vm
assert "ActivityResultContracts.CreateDocument" in ui
assert "CsvEscaper.cell" in export
assert "OutputStreamWriter(zip,Charsets.UTF_8).buffered()" in export
assert "writer.flush()" in export and "buildString" not in export
assert "MAX_SAMPLES+1" in repo and "MAX_EVENTS+1" in repo
assert "samples.size>SessionLibraryPolicy.MAX_SAMPLES" in repo
assert "events.size>SessionLibraryPolicy.MAX_EVENTS" in repo
for name in ("session.csv","samples.csv","events.csv","README.txt"):
    assert name in export
for banned in ("startForegroundService(", "startService(", "deleteDatabase(", "repo.start(", "repo.record(", "repo.finish(", "Settings.System.put"):
    assert banned not in model+vm+ui+export, banned
print("PASS: private read-only history library, parameterized paging, explicit selected-session ZIP")
