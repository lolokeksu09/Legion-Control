# Legion Control — комплексный аудит Alpha 48

Дата: 24 ноября 2026. Объект: кандидат `e537c8a0ab41cf0618ea6c1277258c9743eabdee`, [PR #59](https://github.com/lolokeksu09/Legion-Control/pull/59). Базовая опубликованная Alpha 47: `5004071afba60342a4a489b946f7d8e2d3240286`. Аудит не изменяет приложение, базу данных, разрешения или подпись; изменения этой ветки — только документация. Предыдущие PR #56–58 включены в #59 и не должны независимо сливаться поверх него.

## 1. Резюме, область и доказательства

**Решение:** не заявлять готовность к выпуску, пока не закрыт обязательный CI точного SHA. На момент фиксации последняя опубликованная версия — Alpha 47; Alpha 48 только кандидат.

- Проверено полное дерево GitHub без усечения: 388 файлов, 188 Kotlin-файлов приложения, 52 JVM test-файла, 5 Android instrumented test-файлов, 53 файла scripts/check_*, 54 файла docs/. Количество файлов не равно количеству исполненных тестов.
- Diff Alpha 47 → кандидат: 91 файл, +3367/−79 строк; 41 файл app/src/main, 5 JVM test, 4 Android test, 31 script, 6 docs и 4 прочих. [Точный diff](https://github.com/lolokeksu09/Legion-Control/compare/5004071afba60342a4a489b946f7d8e2d3240286...e537c8a0ab41cf0618ea6c1277258c9743eabdee).
- Просмотрены исходники новых ViewModel/Activity, аналитики, репозитория, манифеста, общей навигации, приватных preferences/backup, telemetry, JNI и CI; прежние не затронутые модули оценены по предыдущему аудиту, диффу и текущим доступным свидетельствам. Это **не** утверждение построчного независимого ревью всех 188 Kotlin-файлов.
- Реально открыты четыре контактных листа, составленные из 46 PNG предыдущего прогона Android 35 x86_64: [run 45321500240, artifact 14888459140](https://github.com/lolokeksu09/Legion-Control/actions/runs/45321500240/artifacts/14888459140). Ревизия `d6ed39f3703dcce92c21802c43759caed8746f66`; она предшествует Session Library и bump версии. XML failure-ui.xml подтверждает package com.google.android.apps.nexuslauncher. В кадрах видны Home/Games/Tools/History/Settings, Capture/Replay, Game Toolbox, Gamepad, Rust, Smart Profiles, Signals/Incidents/Baselines, Audio/Haptic, Gaming/Hardware/Compatibility, Connectivity, Experiment, Application Center, результаты поиска и Live Monitor. Game Preflight, Compare и Library как отдельные экраны в этом артефакте отсутствуют: не заявлять их визуальную проверку.
- Методы: systematic-debugging, security-auditor, database-design, unified-ui-audit, accessibility-compliance-accessibility-audit, performance-profiling, verification-before-completion и requesting-code-review. Применение методик не является сертификатом безопасности или WCAG, автоматическим аудитом сторонних сервисов, профилированием или независимым ревью другим исполнителем.

Обозначения: **OBSERVED** — наблюдено в логе/кадре; **CODE** — конкретное поведение или ограничение исходника; **RISK** — правдоподобный риск без воспроизведения; **GAP** — проверки нет. **P0** — критическая уязвимость/потеря данных, **P1** — до релиза или явное решение владельца, **P2** — ближайший цикл, **P3** — улучшение. P0 в проверенных материалах не подтверждён; это не гарантия отсутствия уязвимостей.

## 2. Реестр изменений Alpha 48

| ID | Изменение | Контракт и проверка |
|---|---|---|
| CH-01 | Function Finder: 79 уникальных действий в 21 модуле | FeatureFinder/FeatureReveal/HubPageFragment, 79 статических привязок, поиск stereo/replay и Settings → backup показаны в предыдущем CI; поиск раскрывает элемент, не нажимает его |
| CH-02 | Live Monitor | 2/5/10 с, максимум 120 точек, 4 графика, контрольная точка, CSV; worker и generation отмены; instrumented lifecycle PASS на предыдущем SHA |
| CH-03 | Game Preflight | 11 проверок Android, индивидуальные требования до 256 пакетов, guarded launch и отчёт; instrumented isolation PASS на предыдущем SHA; отдельные prefs вне ZIP v1 |
| CH-04 | Session Compare | 5/10/15/30 мин, 7 метрик, 3 графика, A/B, CSV; запрет смешивания игр/ID/окон; fixture instrumentation PASS на предыдущем SHA |
| CH-05 | Session Library | Фильтр package/период/завершение, 20 строк на странице, keyset cursor, отдельная сессия ZIP с samples/events; JVM/static/build предыдущего объединённого SHA PASS; runtime exact candidate ещё не закрыт |
| CH-06 | UI и Settings | Двухстрочные подписи вкладок, safe insets, прямые системные переходы, метки доступности; 200%/TalkBack не проверены |
| CH-07 | Release | versionCode 48 / versionName 0.1.0-alpha.48, название APK, notes и 5 новых source contracts; точный кандидат требует CI и постоянной подписи |

См. [ALPHA48-RELEASE-NOTES.md](ALPHA48-RELEASE-NOTES.md), [ALPHA48-FUNCTION-FINDER.md](ALPHA48-FUNCTION-FINDER.md), [ALPHA48-LIVE-MONITOR.md](ALPHA48-LIVE-MONITOR.md), [GAME-PREFLIGHT.md](GAME-PREFLIGHT.md), [SESSION-COMPARE.md](SESSION-COMPARE.md), [SESSION-LIBRARY.md](SESSION-LIBRARY.md). История PR #56–59 и предыдущие release notes сохранены.

## 3. Матрица покрытия продукта

| Направление | Подтверждение | Остаётся |
|---|---|---|
| Home, Games, Tools, History, Settings | OBSERVED: предыдущие кадры и navigation smoke до сбоя; новая функциональная выдача | Полный exact-head smoke, восстановление скролла/фокуса |
| Application Center | Видимый экран, прежняя реализация Launcher/избранного; FeatureReveal | Большие каталоги, разрешения/видимость, пустые/ошибочные данные |
| Game Toolbox | Кадр, прежние compare-before-restore, обработка отказа запуска | Overlay/DND/WRITE_SETTINGS, конфликт с ручным изменением, ROM |
| Smart Profiles | Постоянные подписи и °C видимы (03-smart-profiles.png) | 200% шрифт, TalkBack, реальные действия с профилем |
| Game Session | Предыдущий smoke: START/STOP, ручная метка, SQLite и выбор пакета | Реальная игра, process kill, длительные сессии, foreground restrictions |
| Signals / Incidents / Baselines | Кадры и навигация; недостаток истории явно отображается | Реальные долгие/прерванные записи, пограничные поля/сеть |
| Session Compare | JVM и fixture Android test на предыдущем SHA; source | Экран на кандидате, SAF, пограничные окна, long-session нагрузка |
| Session Library | Source: parameterized SQL, bounded pages, только выбранная запись | Exact Android fixture, пользовательское открытие, SAF/rotation |
| Experiment Lab | Кадр, прежняя SQLite A/B и заметки | Реальные сопоставимые условия, ошибка/экспорт |
| Live Monitor | OBSERVED: 20/21/22 кадры; реальный emulator sample, start/stop/pin, instrumented lifecycle | Экспорт SAF, поворот, battery cost и thermal headroom на Y70 |
| Game Preflight | Source + прежний Android test реального снимка и presets isolation | UI, истечение снимка, возврат из settings, реальная игра, backup |
| Kotlin/Rust diagnostics | JNI неизменён в diff; предыдущий build PASS, снимок диагностики | ARM64 native runtime на Y70, длительная нагрузка |
| Gaming / Hardware / Compatibility Labs | Видимые экраны и прежние backend contracts; 17 новых finder hooks суммарно по каталогу | Реальные датчики/кодеки/OEM/нагрузка, точность и отказ API |
| Connectivity Observatory | Кадр idle; ticket cancellation и generation от Alpha 47 | Offline, DNS зависание, VPN, смена хоста/отмена на реальном Android |
| Audio Lab / Haptic Lab / Gamepad Live | Видимый idle и stereo finder anchor | Реальное звучание, мотор, Bluetooth/USB, отсутствие контроллера |
| Capture Studio, Replay, PNG, Clip Workshop | Кадры idle, consent-координатор, replay/backup anchors; search не запускает Replay | Allow/deny/revoke, реальные MP4/AAC/PNG, replay-save, trim, MediaStore ошибки, DRM |
| Display Patterns | Прежний модуль и маршрут, неизменная базовая реализация | Физическая панель, orientation, OLED/burn-in |
| Безопасность и данные | Manifest diff добавляет только 4 non-exported Activity; SQLite schema/Rust без изменений | Fuzz импортов/Intent, crash/kill, supply chain, частичная запись SAF |
| Доступность / производительность | Source, 46 кадров предшественника; bounded workers/queries | TalkBack/200%/landscape, Android 13/16, профилирование CPU/RAM/энергии |
| CI / подписание | Alpha 47 опубликована; предыдущие сборки PASS | Exact Alpha 48 + подписанный APK + L5 |

## 4. Выводы с приоритетами и критериями

### A48-01 — P1 / OBSERVED + GAP: обязательный UI smoke

**Доказательство:** [45319791539](https://github.com/lolokeksu09/Legion-Control/actions/runs/45319791539), [45320940051](https://github.com/lolokeksu09/Legion-Control/actions/runs/45320940051), [45321500240](https://github.com/lolokeksu09/Legion-Control/actions/runs/45321500240). Все три сборки успешны, инструментальные проверки достигнуты, затем FAIL `Never visible: Готов к запуску` после успешно закреплённой контрольной точки Live Monitor. Failure UI — системный Launcher. Старый back_until повторял Back по невидимому заголовку Home.

**Уже изменено** в #59: один Back → фиксированный GAME HUB header → активная вкладка → поиск заголовка прокруткой. Это обоснованная гипотеза исправления smoke, а не доказательство отсутствия дефекта Android back stack. **Приёмка:** exact candidate 45326107579, затем точный merge SHA и подписанный workflow; никаких skipped/failed gates. В случае сбоя открыть failure-ui.xml и последний кадр, проверить владельца навигации и не ослаблять тест.

### A48-02 — P1 / CODE: «полный» backup не включает новые Game Preflight presets

**Доказательство:** GamePreflightBackend.kt, PreflightPresets.STORE = legion-preflight-v1; LegionDataBackup.kt, names включает только 4 прежних файла. parsePreferences требует точное множество names. На кадре 18c-settings-backup-anchor.png видна «Полная локальная резервная копия». Новые настройки не входят в неё; документация признаёт это. Это не потеря существующих записей SQLite и не новый дефект старого restore.

**Влияние:** при переносе на другое устройство сохранённые требования игры не восстановятся. **Решение:** либо явное предупреждение в UI и согласованный ограниченный объём backup, либо версионированное расширение формата с обратной совместимостью v1. Не добавлять имя в strict set без поддержки старых ZIP. Проверить валидность package/key/value/лимиты, journal recovery с прежними и новыми архивами, отрицательные и round-trip тесты. Приёмка — один профиль двух игр экспортируется и восстанавливается или явно обозначен исключённым *до* экспорта.

### A48-03 — P2 / CODE: экспорт Compare/Library теряет выбранный результат при пересоздании Activity

**Доказательство:** SessionCompareActivity.kt: pendingExport (стр. 37–42); SessionLibraryActivity.kt: pendingExport и ActivityResultContracts.CreateDocument. Состояние отчёта хранится в ViewModel, но pendingExport — поле Activity, не восстановлено из Bundle/ViewModel. При пересоздании во время системного выбора документа callback может получить URI при null pendingExport; файл создан, запись не выполняется, UI просит повтор. Защита от подмены результата сохранена.

**Исправление:** единый устойчивый export request token + snapshot в ViewModel, блокировка повторного picker, проверка target identity; если процесс убит, явное сообщение о невозможности продолжения, без экспорта другой записи. Test ActivityScenario.recreate во время picker и отмена/повтор, затем распаковать фактический файл. Для LiveMonitor также нужен явный frozen snapshot до запуска picker и обработка ошибки самого launch (LiveMonitorActivity.kt:111–112). Не считать запись на произвольный SAF URI атомарной: provider может оставить частичный файл при I/O отказе.

### A48-04 — P2 / CODE: выделение вкладки не соответствует новым standalone модулям

**Доказательство:** LegionUiSupport.kt:325–329, selectedIndex = 1 для 4 старых игровых Activity, иначе 2. GamePreflightActivity, SessionCompareActivity и SessionLibraryActivity попадают в Tools, хотя логически принадлежат Games/History; Live Monitor открыт также с Home. Старые native screens в NavigationHostActivity имеют отдельный owner mapping.

**Влияние:** неверное состояние TalkBack/selected и непоследовательная ориентация пользователя. **Приёмка:** единая таблица owner для standalone и NavHost; Game Preflight → Games, Compare/Library → History, Monitor → Tools (или явно согласованная модель контекстного origin). Проверить вход из Home, Games, History, Search, карточки игры и Back; всегда ровно один selected.

### A48-05 — P2 / CODE + GAP: функциональный поиск зависит от текста и состояния экрана

**Доказательство:** FeatureReveal.kt: findLabel возвращает первый TextView с точным текстом; targetOf ищет clickable ancestor; reveal выполняется один раз после layout и не проверяет isEnabled. FeatureFinder хранит 79 IDs, но anchors — строки UI; static check подтверждает совпадение текста, не каждую динамическую привязку. На кадрах 18a/18b/18c раскрытие подтверждено для конкретных трёх действий.

**Риск:** disabled/export без данных, повторяющийся заголовок, динамическая перерисовка или перевод могут привести к неверному/недоступному target. **Исправление:** stable control IDs/tags и доступность с причиной, ожидание конкретного состояния без автоматического нажатия. Приёмка: параметризованный маршрут всех 79 IDs, неизвестный/чужой module ID не действует, поиск не начинает захват/запись, clear/no-match, сохранение query после Back/rotation.

### A48-06 — P2 / CODE + RISK: настройки зависят от загрузки несвязанной сводки

**Доказательство:** HubPageFragment.render: Loading/Failed особым образом обслуживают только Tools; showSettings вызывается в Ready. При ошибке локальной истории настройка разрешений и backup shortcuts не отображаются штатно. Это следует из branch кода, ошибка на устройстве не воспроизведена.

**Исправление:** независимая доступность безопасных системных Settings и recovery даже при ошибке HubSnapshot; локальные карточки показывают собственный отказ. Приёмка: имитировать repository error, открыть официальный permission/settings и recovery, не терять retry.

### A48-07 — P2 / CODE + RISK: Preflight freshness и UX статуса

**Доказательство:** GamePreflightActivity.kt:136–145, launch.isEnabled проверяет report!=null; fresh учитывается для текста и непосредственно requestLaunch/launchChecked. ViewModel setVisible(false) вызывается в onStop. Защита запуска есть, но по истечении 60 с без нового render кнопка/summary могут выглядеть актуальными. Частично перекрытая Activity (например, split-screen) не обязательно получает onStop; это не гарантия неизменности Android.

**Исправление:** отдельные состояния fresh/stale/shareable; таймер истечения на видимом экране, понятная кнопка повторного снимка, дополнительная проверка при возобновлении/смене настроек; не блокировать share старого отчёта. Зафиксировать время начала/конца чтения и то, что 11 Android readings не атомарный снимок. Приёмка: expiry, onStop/resume, change preset, late completion, предупреждения и критические температуры; реальный Launcher Intent запрашивается заново.

### A48-08 — P2 / CODE + GAP: границы аналитики и понятность показателей

**Доказательство:** SessionCompareAnalysis.kt: 70% coverage, 3+ known readings, 45 с gap, 2000 sample cap, same-package/distinct-ID; percentages являются долями известных замеров. Эндпоинты A/B могут различаться в пределах допуска 45 с, заряд может расти при зарядке. CompareWindow.duplicateOrOutside считает повторы и значения раньше начала, но не все out-of-window values; поле не выводится в отчёт. LiveMonitor baseline battery difference подписана % (точнее п.п.); график показывает диапазон в тексте, не полноценные числовые оси/временную сетку. Ни один экран не утверждает FPS или игровой ping.

**Исправление:** явно показывать observed endpoint offsets, known/total, пределы/длительность и зарядку как confounder; переименовать счётчик либо считать только осознанно определённые категории; для baseline charge use percentage points. Добавить легенду/оси/доступный текст/таблицу выбранных измерений. Не вводить псевдоскор или оценку FPS. Приёмка: пропуски, invalid/NaN, 0/1/2 samples, duplicate timestamp, зарядка, A/B swap и сравнение одинаковых окон.

### A48-09 — P2 / RISK + GAP: производительность и ресурсы

**Доказательство:** LiveMonitorViewModel делает GameSessionTelemetry.snapshot + полный ResourceTelemetryBackend.read каждые 2/5/10 с. Последний читает также StatFs, процессоры, несколько battery properties, thermal headroom с gate, хотя монитор использует лишь RAM/headroom. Это ограниченная работа вне UI, не измеренная утечка. SessionLibraryViewModel держит 20 rows и до 10000+2000 points/events выбранной записи; экспорт строит строки CSV в памяти; SessionCompare ограничивает чтение 2001 points. Library keyset query использует started_at_ms,session_id, package filter; существующий индекс idx_game_sessions_started только started_at_ms. Нет реального EXPLAIN QUERY PLAN на большой пользовательской БД.

**Исправление:** до оптимизации провести замеры p50/p95 времени/heap, query plan и battery overhead на 100/1000/10000 записей и длительном 120-point monitor; затем при необходимости узкий read API, stream CSV, индекс после анализа миграции, не менять SQLite v12 вслепую. Проверить быстрые paging/filter, отмену на выходе, low memory, долгую выгрузку. Никаких выдуманных CPU/FPS/ANR процентов.

### A48-10 — P2 / OBSERVED + GAP: доступность и информационная плотность

**Доказательство:** NavigationHostActivity и LegionUiSupport задают 10sp подписи вкладок, maxLines=2, 57dp minHeight; buttons 56dp. В кадрах подписи и многочисленные технические описания мелкие, длинные одноколоночные экраны требуют прокрутки; Live Monitor baseline/график на отдельной части экрана. Это не формальное измерение контраста/нарушение WCAG. У старых UI остаётся mixed RU/EN naming и повтор заголовка. У новых chart custom View importantForAccessibility=NO, есть текст min/max, но нет альтернативы отдельных значений/тренда. Настройки диалогов и клавиатуры не проверены.

**Приёмка:** 100/150/200% fontScale, 320dp/широкий viewport, portrait/landscape, TalkBack focus order, accessible selected/disabled, 48dp targets, screen reader summaries, IME/insets. Проверить contrast text/disabled/selected по реальным цветам, без объявлений WCAG AA до теста. После — унификация токенов, названий, приоритетных CTA и кратких status/error states; не заменять рабочий backend ради косметики.

### A48-11 — P2 / GAP: реальные Android-права, media и ROM

**Доказательство:** Manifest оставляет 14 permissions, включая существующие RECORD_AUDIO для playback capture, overlay/WRITE_SETTINGS/DND/usage access и FGS. CaptureCoordinator синхронизирует режимы; GameSessionRecorderService START_NOT_STICKY; public launcher и QS Tile (BIND_QUICK_SETTINGS_TILE) — единственные экспортированные входы. Новые 4 Activity приватны, allowBackup=false/usesCleartextTraffic=false. В просмотренных кадрах только idle/anchors; не доказана запись MP4/AAC/PNG или реальный Replay. Внешний Launcher Intent может передавать extras; в NavigationHostActivity существует allowlist module routes, нет доказательства злоупотребления.

**Приёмка:** Lenovo Android 13/ROM + API35/36: allow/deny/revoke для каждого специального доступа, MediaProjection single-owner и отмена согласия, START/STOP+pause/resume+Replay+AAC и DRM, file provider failures, app process kill/relaunch, BT/USB, звук/вибрация. Negative external Intent tests (unknown route, invalid bundle types, no auto operations). Не расширять привилегии. Аудит privacy: данные локальны кроме явно выбранной сети/share; проверить фактические сетевые обращения. Не заявлять отсутствие всех уязвимостей.

### A48-12 — P2 / CODE + GAP: backup recovery и предыдущие AUD-02/04/05/06

**Доказательство:** LegionDataBackup journal+application_id и strict names, FullBackupInstrumentedTest post-COMMIT exception PASS на предыдущих SHA; LocalHistoryStore foreign keys enabled, schema unchanged. ConnectivityLabViewModel использует ConnectivityRunTicket, generation host change и cancel-before-start; GameLaunchProfiles compare-before-restore. Это исправления Alpha 47, перенесены без регрессии исходников. Настоящий OS process kill на checkpoint, системная DNS отмена/смена сервера и launch SecurityException на ROM не подтверждены.

**Приёмка:** восстановление на чистой установке, повреждённый ZIP/oversized, populated DB rejection, process kill pre/post COMMIT и при prefs commit, повтор запуска; DNS/cancel/host swap и rollback conflict. Никакого удаления пользовательских данных; только disposable test environment.

### A48-13 — P2 / RISK: воспроизводимость и supply chain выпуска

**Доказательство:** app/build.gradle.kts закрепляет версии direct dependencies и SDK/NDK/Gradle; Cargo.lock имеется. Дерево не содержит Gradle dependency lock/verification-metadata, Dependabot/CodeQL/SBOM. Workflows используют refs @v4, @stable вместо immutable full SHA. Signed workflow имеет contents:write и actions:read, секреты восстанавливает в runner.temp; проверка APK/pinned cert и exact-main CI присутствует. Это supply-chain риск, не обнаруженный компромисс ключа. Секреты не запрашивались и не читались.

**Исправление:** отдельная согласованная поставочная hardening-задача: lock/verification metadata, pin reviewed action commits и Rust toolchain, dependency scan/SBOM, защитить signing secrets/branch, least privilege на шагах публикации, cleanup keystore. Добавить release provenance SHA и checksum; проверить fail-closed при отсутствии/неверной подписи. Не менять сертификат. Не выдавать artifact из disposable CI как production APK.

### A48-14 — P3 / CODE: сопровождаемость версий и повторение UI/worker/export

**Доказательство:** bump 47→48 изменил 25 исторических Python source checks с константами версии; новые Activity повторяют создание label/button и chooser/export flow; ViewModel имеют отдельные executor/Handler lifecycle. Это реальная стоимость сопровождения, не основание для полной переписи.

**Исправление:** единый источник versionCode/versionName и проверка согласования артефакта/тега, отделить исторические feature assertions от текущей версии. Выделять общий экспортный request protocol и shared UI primitives только после тестов A48-03/04, без массовой миграции в Compose. Принять только локальные изменения, которые убирают подтверждённое дублирование и сохраняют поведение.

## 5. Статус прежнего аудита

| Alpha 46 finding | Статус в кандидате |
|---|---|
| AUD-01 | Подписи и °C реализованы; видимы в предыдущем снимке. A48-10: крупный шрифт/TalkBack остаются |
| AUD-02/03 | Journal/marker и инструментальный roundtrip с fault injection реализованы/предыдущий PASS; реальный kill остаётся A48-12 |
| AUD-04/05 | Ticket/generation реализованы; реальные сетевые сценарии A48-12 |
| AUD-06 | Rollback при синхронном отказе реализован; реальные разрешения/конфликты A48-11/12 |
| AUD-07 | Старые версии сняты в четырёх экранах, общий version label; nomenclature остаётся A48-10 |
| AUD-08 | Двухстрочная nav, insets и accessibility metadata реализованы; не закрыт runtime a11y, A48-04/10 |
| AUD-09 | Открыт, аппаратная приёмка A48-11 |
| AUD-10 | 79 IDs, Settings shortcuts реализованы; A48-05/06 остаются |
| AUD-11 | Частично: общий shell, но смешанные подписи и плотность A48-10 |

## 6. Матрица проверок и релизный статус

| Gate / ревизия | Наблюдение |
|---|---|
| Alpha 47 release | [опубликован](https://github.com/lolokeksu09/Legion-Control/releases/tag/v0.1.0-alpha.47); main 5004071a; прежние exact main Android и permanent signed CI |
| 18af6f5b / 45319791539 | build PASS; backup+monitor instrumentation PASS; UI smoke FAIL |
| e7a15934 / 45320940051 | build PASS; backup+monitor+preflight instrumentation PASS; UI smoke FAIL |
| d6ed39f3 / 45321500240 | build PASS; backup+monitor+preflight+compare fixture instrumentation PASS; UI smoke FAIL; 46 кадров просмотрены |
| 3c0b96e3 / 45324619575 | build PASS; emulator running на момент аудита |
| e537c8a0 / 45326107579 | build running на момент аудита; emulator pending; **exact-candidate L4 не закрыт** |
| Alpha 48 exact main signed APK | NOT RUN / не опубликован |
| Physical Android 13 Lenovo, Android 16, TalkBack/200%, performance, security dynamic | NOT RUN |

Источники: [candidate CI](https://github.com/lolokeksu09/Legion-Control/actions/runs/45326107579), [integrated predecessor](https://github.com/lolokeksu09/Legion-Control/actions/runs/45324619575), [old failure artifact](https://github.com/lolokeksu09/Legion-Control/actions/runs/45321500240/artifacts/14888459140), [previous full audit](FULL-AUDIT-ALPHA46.md), [remediation](ALPHA47-AUDIT-REMEDIATION.md), [TESTING-LEVELS](../TESTING-LEVELS.md). CI статусы — снимок на момент фиксации, а не динамический отчёт.

## 7. План устранения и решение

1. P1: A48-01 закрыть exact-candidate smoke, документировать результат. A48-02 принять решение о backup coverage, без незаметной несовместимости старых архивов. Не публиковать как полностью проверенную версию без обязательных gates.
2. P2: A48-03/04/05/06/07 — экспорт, навигация, функциональный поиск, recovery/Settings, stale UI. Точечные патчи с отрицательными тестами.
3. P2: A48-08/09/10 — измерения, корректное отображение, Android accessibility/performance; затем A48-11/12 — аппаратные/permission/process death сценарии с отдельным отчётом.
4. P2/P3: A48-13/14 — hardening CI и снижение связности; без замены ключа и стека.
5. Для каждой правки приложить exact SHA, тест, артефакт/лог и статус. Доказательство с другой ревизии не закрывает exact-SHA gate. Отдельное ревью safety/data paths желательнее новых косметических патчей.

**Итог:** подтверждены реальные прежние runtime-пути и качественные свойства новых исходников, есть конкретные ограничения и 14 задач приёмки/устранения (2 P1, 11 P2, 1 P3). Не обнаружено подтверждённых P0 в рассмотренных материалах. Это результат аудита, а не сертификат production-readiness. Изменения аудита документальные; исправления не заявлены как выполненные, кроме уже существующих CH и smoke correction.
