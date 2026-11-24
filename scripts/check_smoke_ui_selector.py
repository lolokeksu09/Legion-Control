#!/usr/bin/env python3
"""Regression fixture: coordinates/labels from failed Beta 1 emulator hierarchy."""
import xml.etree.ElementTree as ET
from smoke_ui_selector import locate

tree = ET.fromstring('''<hierarchy>
<node text="Игры, инструменты и локальная история — в одном месте." bounds="[49,522][1031,625]" clickable="false"/>
<node content-desc="Открыть раздел Главная" bounds="[13,2129][223,2261]" clickable="true" enabled="true"/>
<node text="Главная" bounds="[13,2193][223,2253]"/>
<node content-desc="Открыть раздел Игры" bounds="[223,2129][434,2261]" clickable="true" enabled="true"/>
<node content-desc="Открыть раздел Инструменты" bounds="[434,2129][645,2261]" clickable="true" enabled="true"/>
<node text="Инстр." bounds="[434,2193][645,2253]"/>
<node content-desc="Game Toolbox. Плавающая панель" bounds="[49,1304][1031,1474]" clickable="true"/>
</hierarchy>''')
assert locate(tree, "Инструменты") == (539,2195)
assert locate(tree, "Инструменты", True) == (539,2195)
assert locate(tree, "Главная", True) == (118,2195)
assert locate(tree, "Игры") == (328,2195)
assert locate(tree, "Game Toolbox") == (540,1389)
assert locate(tree, "Game Toolbox", True) is None
assert locate(tree, "missing") is None
print("PASS: exact accessible bottom navigation overrides matching home copy")
