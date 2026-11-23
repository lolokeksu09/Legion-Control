# Legion Control Alpha 46 — комплексный аудит

**Дата:** 23 ноября 2026  
**Ревизия приложения:** `3cd43f2449ea4cdba86aa3b6e86f13e725a4fce3` (`main`)  
**Релиз:** [v0.1.0-alpha.46](https://github.com/lolokeksu09/Legion-Control/releases/tag/v0.1.0-alpha.46)  
**Статус:** аудит завершён в рамках доступного исходного кода и CI-артефактов; физические испытания **НЕ ВЫПОЛНЕНЫ**.  
**Характер изменений:** только документирование; приложение, данные и подписание не модифицируются.

## 1. Источники и метод

1. Получено точное дерево GitHub для `main` (342 файла, без усечения); в нём 167 Kotlin-файлов приложения, 45 Kotlin unit-test-файлов, один instrumented-test-файл, 48 `scripts/check_*.py`, 45 файлов `docs/`.
2. Просмотрены фактические исходники навигации, основных вкладок, диагностик, Game Session, Game Toolbox, Smart Profiles, Application Center, Connectivity, Audio, Haptic, Capture/Replay/Screenshot, MediaStore, SQLite, импорта/экспорта, AndroidManifest и CI.
3. Исследованы **39 PNG-скриншотов** из артефакта `Legion-Control-alpha29-android35-ui-smoke` ([артефакт 14859096033](https://github.com/lolokeksu09/Legion-Control/actions/runs/45252918742/artifacts/14859096033)), созданных Android 35 x86_64 эмулятором на этой же ревизии.
4. Проверены GitHub Actions: [Kotlin/Rust и эмулятор](https://github.com/lolokeksu09/Legion-Control/actions/runs/45252918742) — **PASS**, [постоянно подписанный APK](https://github.com/lolokeksu09/Legion-Control/actions/runs/45252918785) — **PASS**. Сверена публикация Alpha 46 и точный SHA.
5. Сопоставлены границы автоматического покрытия с `.github/workflows/android.yml`, `scripts/check_android_emulator_smoke.py` и `app/src/androidTest/java/dev/lolokeksu/legioncontrol/FullBackupInstrumentedTest.kt`.

**Термины:** `OBSERVED` — подтверждено на реальном кадре/в фактическом логе; `CODE` — подтверждена конструкция исходников, но последствия не воспроизведены на устройстве; `GAP` — сценарий не выполнялся или не был покрыт, **не доказанный дефект**. Приоритеты: **P1** — до следующего публичного релиза; **P2** — ближайший цикл; **P3** — технический долг/полировка.

## 2. Результат по ключевым областям

| Область | Реализовано/проверено | Граница доказательства |
| --- | --- | --- |
| Пять основных вкладок и вложенная навигация | **PASS в эмуляторе**: Home, Games, Tools, History, Settings; Back, Game Session, Game Toolbox, Smart Profiles, Audio/Hardware/Gaming Lab, Session Signals/Baselines/Incidents | Не проверены большие шрифты, ландшафт, TalkBack и все внешние Intent-переходы |
| Игровая библиотека и Application Center | Реальное перечисление видимых Launcher-приложений, профиль/избранное и переходы; кадры библиотек существуют | Не проверены тысячи установленных приложений, запрет видимости и загрузка данных после полного восстановления |
| Game Session / Signals / Incidents / Baselines | **PASS в эмуляторе**: ручной START/STOP, метка, SQLite-сохранение, связанные экраны и история | Не измерены игровые FPS, фактическая нагрузка на Legion Y70, долгие реальные сессии и системное убийство процесса |
| Capture Studio, MP4, AAC, Replay, Screenshot, Clip Workshop | Присутствуют действующие сервисы, явный MediaProjection consent, единственный активный захват, MediaStore owned-file policies; доступны кнопки | **NOT RUN**: реальное согласие/отмена, качественная запись MP4/AAC, DRM, хрупкие ROM/кодеки, Replay-save, PNG, Clip Workshop |
| Game Toolbox и профили запуска | Плавающая панель/действия присутствуют, кнопки и экран открываются; приложение использует официальные Android API | **NOT RUN**: предоставление/отзыв overlay, DND/WRITE_SETTINGS на Lenovo, конфликт глобальных настроек |
| Smart Profiles | UI присутствует; 6 правил и рабочие repository/ViewModel; видна форма | **OBSERVED дефект**: два заполненных числовых поля визуально без подписей |
| Connectivity Observatory | DNS, IPv4/6, TCP/TLS, история/экспорт имеют реализацию; экран виден | **NOT RUN**: реальная сеть/offline/timeout/cancel/host switch |
| Audio Lab, Haptic Lab, Gamepad Live | Ручные действия, состояние idle, кнопки и маршруты видимы | **NOT RUN**: фактическое звучание, вибромотор, физический контроллер и Bluetooth |
| Gaming, Hardware, Compatibility Lab, Kotlin/Rust diagnostics | Тесты и отчёты сгруппированы; видны экраны; JNI и библиотеки собраны | В эмуляторе не доказаны аппаратная точность, вендорские ROM и ложные/недоступные сенсоры |
| Локальные данные, импорт, экспорт | SQLite v12, резервный ZIP с SHA-256, ограничения размера/структуры, отказ импортировать поверх непустой БД, локальные CSV и отдельные MP4 | Существуют риски crash consistency; instrumented backup test **собран, но не выполнен** |
| Разрешения, приватность и поставка | `allowBackup=false`, `usesCleartextTraffic=false`, отсутствие Root/Shizuku/QUERY_ALL_PACKAGES; внутренняя activity/service isolation; постоянно подписанный релиз подтверждён | Точечные отказы/повторные запросы разрешений и Android 13/16 на устройствах не проверены |

## 3. Конкретные выводы

### AUD-01 — P1 / OBSERVED: неподписанные числовые поля Smart Profiles

- **Доказательство:** снимок `03-smart-profiles.png` в артефакте UI, а также `SmartProfilesFragment.kt` ~60–84. Поля создаются с `hint` и сразу заполняются `420` и `20`; после ввода hint исчезает. Пользователь видит числа без названия, единицы измерения и подсказки о диапазоне.
- **Эффект:** можно спутать температуру порога (42.0°C представлена в дециградусах) с зарядом батареи; экран плохо читается с TalkBack и при крупном шрифте.
- **Исправление:** постоянные видимые подписи «Температура предупреждения» и «Заряд предупреждения», понятный формат °C (не технический `°C × 10`), пересчёт перед сохранением, `labelFor` и описание для assistive technology, положительный и отрицательный ввод.
- **Приёмка:** после холодного запуска и загрузки профиля каждое число однозначно ассоциировано с названием/единицей, 200% шрифт и TalkBack проходят ручную проверку.

### AUD-02 — P1 / CODE: SQLite-транзакция и четыре SharedPreferences не образуют атомарный restore

- **Доказательство:** `LegionDataBackup.kt` ~92–170 и ~375–391: `target.beginTransaction()` → INSERT таблиц → `applyPreferences()` (четыре последовательных `editor.commit()`) → `setTransactionSuccessful()`.
- **Риск:** аварийное завершение процесса/разряд между commit файлов и фиксацией SQLite может оставить часть новых предпочтений при откатанной базе; catch восстанавливает настройки лишь если исключение дошло до Kotlin.
- **Исправление:** явное долговечное состояние процедуры и crash recovery, либо staged settings/журнал восстановления и проверка после рестарта; нельзя утверждать атомарность разных хранилищ только из-за SQLite transaction.
- **Приёмка:** fault injection после каждого шага, смерть процесса в каждом checkpoint, проверка прежних/новых данных и повторяемого восстановления без полусостояния.
- **Оговорка:** нет воспроизведённого случая потери данных на устройстве. В штатном `FullBackupInstrumentedTest` проверяются целые архивы и ошибки, но не падение процесса в середине `applyPreferences`.

### AUD-03 — PASS / CORRECTED: instrumentation-тест backup уже исполняется в CI

- **Доказательство:** `scripts/check_android_emulator_smoke.py` в `main` (строки 218–231) **сам** устанавливает `app-debug-androidTest.apk`, запускает `adb shell am instrument -w -e class dev.lolokeksu.legioncontrol.FullBackupInstrumentedTest dev.lolokeksu.legioncontrol.test/androidx.test.runner.AndroidJUnitRunner` и отвергает результат без `OK (1 test)`. В логах CI API 35 есть `PASS: emulator-only SQLite backup/restore and corruption round-trip`. Это была ошибка первоначального аудита; проверка выполняется.
- **Результат:** backup roundtrip, отказ восстановления поверх заполненной базы и искажённый SHA-256 действительно проверяются инструментальным тестом в эмуляторе. **Не покрыто:** процесс-kill внутри последовательности `applyPreferences()` — остаётся AUD-02.
- **Исправление не требуется.** Существующую инструментальную проверку сохранить; не расширять CI без нового непокрытого сценария.
- **Гейт:** `am instrument` требует `OK (1 test)` и отсутствие `FAILURES`; результат PASS на коммите `3cd43f24`.

### AUD-04 — P2 / CODE: отмена Connectivity до старта фоновой задачи может оставить `testing=true`

- **Доказательство:** `ConnectivityLabViewModel.kt` ~52–65 и ~108–138: CAS `testing=false→true` ставится до `worker.submit`; `cancelTest` вызывает `Future.cancel(true)`, сброс `testing=false` находится только во `finally` задачи.
- **Риск:** если задача отменена ещё в очереди (до первого выполнения тела), её `finally` не вызовется; UI может остаться заблокированным как «тест активен».
- **Исправление:** управлять жизненным циклом задачи отдельным состоянием/generation, гарантировать `idle` для `cancel-before-start`, защищать от повторного запуска до корректной остановки.
- **Приёмка:** детерминированный executor-тест с блокированным worker, старт → мгновенная отмена → тест не запущен, UI возвращён в idle; затем успешный повторный старт.

### AUD-05 — P2 / CODE: смена целевого сервера не инвалидирует ожидающие ответы Connectivity

- **Доказательство:** `ConnectivityLabViewModel.chooseHost` ~42–50 меняет `host`, но не увеличивает `generation`. Очередной `showHistory`/`compare` в `submit` использует generation для защиты устаревшего ответа.
- **Риск:** результат старого сервера может появиться после смены на другой; это вопрос соответствия источника данных выбранному узлу, не доказанный сетевой дефект.
- **Исправление:** инвалидировать generation при смене хоста, зафиксировать request-key в задаче и сверять его перед публикацией.
- **Приёмка:** Cloudflare → запрос истории → Google до ответа → UI не показывает результаты Cloudflare под текущим Google.

### AUD-06 — P2 / CODE: ошибка открытия игры после применения Game Launch Profile оставляет изменения

- **Доказательство:** `GameToolboxFragment.kt` ~322–341: `GameLaunchProfiles.apply(...)` вызывается до `startActivity(launchIntent)`; обработчик `onFailure` меняет только текст, не вызывает `GameLaunchProfiles.restore`.
- **Риск:** громкость/яркость/DND могли измениться, хотя игра не запустилась. Отдельная кнопка возврата есть, но пользователь может не заметить.
- **Исправление:** восстановить профиль при ошибке запуска с сообщением о результате, либо явно предложить немедленный возврат. Не восстанавливать сторонние изменения вслепую: сохранить compare-before-restore.
- **Приёмка:** симулировать ActivityNotFoundException и SecurityException после успешного apply, проверить реальное/эмулированное восстановление каждого поля и информирование об оставшихся конфликтах.

### AUD-07 — P2 / OBSERVED: старые номера Alpha во внутренних модулях

- **Доказательство:** `ConnectivityLabFragment.kt` содержит описание `alpha.20`, `AudioLabFragment.kt` — `ALPHA.22`, `HapticLabFragment.kt` — `ALPHA.23`, `ApplicationCenterFragment.kt` — `alpha.21`; они видны на Alpha46 screenshots.
- **Эффект:** пользователь получает противоречивые версии, выглядит как несогласованный продукт.
- **Исправление:** убрать внутренние исторические версии из UI, оставлять только общую версию приложения `LegionUiSupport.versionLabel` на главном уровне.
- **Приёмка:** весь screenshot sweep соответствует Alpha46/новой версии, ни один экран не подписан исторической Alpha.

### AUD-08 — P2 / OBSERVED + GAP: мелкие элементы и невалидированная доступность

- **Доказательство:** `NavigationHostActivity.kt` и `LegionUiSupport.kt` задают подписи 5 нижних вкладок `10sp`; некоторые заголовки форсируются к `12sp`. На кадрах видны мелкий плотный текст и перегруженные одноколоночные экраны.
- **Не доказано:** автоматического контрастного/ТalkBack/200%-font/landscape аудита в CI нет; формальную WCAG 2.2 AA/EN 301 549 совместимость заявлять нельзя.
- **Исправление:** заменить ручные спейсинги/минимальные размеры компонентов токенами дизайн-системы, измерить truncation при fontScale 1.5–2.0, проверить доступные цели 48dp и TalkBack focus/order/state announcements.
- **Приёмка:** 5 вкладок, формы, диалоги, длинные имена приложений остаются разборчивыми при 200% шрифте; выбранная вкладка объявляется; поля ввода имеют постоянные label.

### AUD-09 — P2 / GAP: физические media/permission/device сценарии не покрыты

- **Доказательство:** эмуляторный smoke содержит экранные шаги, пустые/idle лаборатории и Game Session; нет проверки записанных реальных MP4/AAC, скриншотов MediaProjection, Replay, USB/Bluetooth gamepad и хаптики. В workflow нет проверки Lenovo.
- **Исправление:** bounded manual test plan на физическом Lenovo Legion Y70: MediaProjection ALLOW/DENY/REVOKE, повторное согласие, 5-секундный скриншот, Replay 30/60/120 с/без разрешённого AAC, запись-остановка, смена приложения и восстановление UI, DND/WRITE_SETTINGS/overlay allow/deny, сетевой offline/timeout, BT контроллер, звук, вибрация, длительная игра.
- **Приёмка:** отдельный L5 отчёт с build SHA/сертификатом/Android ROM, ожидаемым и наблюдённым результатом, без объявления виртуальных показателей «FPS игры».

### AUD-10 — P2 / GAP: информационная архитектура не доведена до полного каталога

- **Доказательство:** `HubPageFragment.kt` ~337–417 фильтрует только 11 модулей/группы по названию и краткому назначению, не отдельные функции; `showSettings` ~458–489 представляет набор переходов. `docs/FEATURE-INFORMATION-ARCHITECTURE.md` прямо оставляет несколько задач за рамками PR #51.
- **Исправление:** единый реестр операций с owner-tab, условиями доступности, статусами и deeplink в конкретную подфункцию; один центральный Settings-срез (разрешения, безопасность данных, prefs, MediaStore, DND, backup).
- **Приёмка:** из глобального поиска доступны конкретные действия без дублирования логики и не существует «мёртвых» кнопок/некорректных deeplink.

### AUD-11 — P3 / OBSERVED: экранные тексты и навигационные названия не стандартизованы

- **Доказательство:** смешение `LEGION CONTROL`, `GAME HUB`, `APPLICATION CONTROL CENTER`, `CONNECTIVITY OBSERVATORY`, русских системных названий и «alpha»-маркировок в одном релизе. Некоторые экраны используют длинные вводные дисклеймеры перед главным действием.
- **Исправление:** словарь наименований, русские основные заголовки при неизменных именах модулей, краткое однофразное предупреждение перед опасной операцией, технические подробности в «О проверке».
- **Приёмка:** единообразные title/subtitle/empty/error states, быстро доступна основная задача каждого модуля.

## 4. Отмеченные сильные стороны (проверено по исходникам и CI)

- Единственная публичная Launcher Activity; остальные экраны и фоновые сервисы не экспортируются, кроме Quick Settings Tile с системным разрешением.
- `AndroidManifest.xml` не просит Root/Shizuku или `QUERY_ALL_PACKAGES`; используется видимость Launcher, отказоустойчивые публичные Android API.
- Медиа захваты требуют свежего системного согласия, `CaptureCoordinator` исключает конкурирующие записи, MediaStore-операции ограничены собственными файлами.
- `LegionDataBackup` валидирует названия ZIP-элементов, SHA-256, schema 12, структуру таблиц и лимиты; не перезаписывает непустую SQLite. Риск AUD-02 относится именно к power-loss atomicity, не отрицает остальных защит.
- У профилей запуска есть persist undo и compare-before-restore, чтобы не затирать изменения пользователя.
- CI тестирует Kotlin/Rust, обе JNI ABI, debug APK, negative signing test и pinned permanent release certificate. На Alpha 46 все стандартные jobs завершились успешно.

## 5. Матрица проверок перед Alpha 47

| Гейт | Проверка | Платформа | Статус |
| --- | --- | --- | --- |
| L1/L2 | Pure Kotlin/JVM, 48 Python проверок, Rust и контракт разрешений | GitHub CI exact main SHA | **PASS** |
| L3 | Debug APK, навигация, Game Session marker, SQLite для smoke, поиск, 39 кадров | Android 15 / API 35 x86_64 emulator | **PASS** |
| L3 | Настоящий `FullBackupInstrumentedTest` | Чистый CI Android emulator | **PASS** — `am instrument`, `OK (1 test)`, CI 45252918742 |
| L3 | MediaProjection MP4/AAC, Replay, Screenshot, отрицательные разрешения | Android emulator + hardware | **NOT RUN** |
| L2/L3 | Network immediate cancel, host swap and stale response | Controlled executor + Android | **NOT RUN** |
| L2/L3 | Cross-store backup process-kill failure injection | CI emulator | **NOT RUN** |
| L4 release | Постоянный сертификат, native ABIs и signed artifact, exact SHA check | GitHub signed release | **PASS** |
| L5 device | Реальный звук, вибрация, BT-геймпад, ROM permissions, запись игрового процесса | Lenovo Legion Y70 | **NOT RUN** |
| Accessibility | TalkBack, 200% system font, landscape, display cutout | Android emulators + Y70 | **NOT RUN** |

## 6. План устранения и безопасный порядок

1. **P1:** подписать поля порогов Smart Profiles (AUD-01); выстроить crash-safe backup restore и процесс-kill regression (AUD-02). **AUD-03 закрыт без кода**: instrumented test уже запускается в CI.
2. **P2:** исправить cancel-before-start и stale-host response в Connectivity (AUD-04/05); rollback на отказ запуска игры (AUD-06); унифицировать UI version labels и проверить fontScale/TalkBack (AUD-07/08).
3. **P2:** добавить аппаратный smoke-план и evidence на Y70, MediaProjection, разрешения, AAC и BT controller (AUD-09).
4. **P2/P3:** сквозной функциональный поиск, самостоятельный Settings и общий glossary (AUD-10/11).
5. Любая затрагивающая данные/сервисы правка — **отдельный PR и точечный тест** плюс CI полного точного SHA. Не менять SQLite v12/подпись без специального решения; не выпускать новую Alpha только из-за аудита.

## 7. Границы и честный вывод

**Alpha 46 — реальный собираемый и опубликованный продукт**, большинство именованных модулей имеют рабочий backend. Но «весь функционал полностью готов и проверен» утверждать нельзя: функциональные пути hardware/media, crash consistency и часть UX/доступности не закрыты тестами. Дефекты AUD-01 и AUD-07 наблюдались на скриншотах; AUD-02, AUD-04, AUD-05 и AUD-06 — подтверждённые **сценарии риска в коде**, но не воспроизведённые аварии; AUD-03 исправлен после проверки реального CI-скрипта; остальные пункты — gaps. Цель Alpha 47 — адресно закрыть P1, затем P2 с доказательствами, а не переписывать приложение с нуля.
