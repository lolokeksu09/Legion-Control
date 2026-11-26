# Особенности проекта — Legion Control

**Целевая переработка 0.2.0:** [Unified Architecture — требования, этапы и приёмка](docs/UNIFIED-ARCHITECTURE-0.2.0.md). Текущая версия остаётся alpha.59; план не означает завершённую реализацию. Следующий срез: единый центр игровой аналитики.

Актуальный реестр работ: [аудит 26.11.2026](docs/AUDIT-2026-11-26.md), [Stability & Safety](docs/STABILITY-SAFETY-2026-11-26.md). Эти документы уточняют исторические сведения о релизах и проверках ниже.

Статус: адаптировано по фактическому состоянию репозитория `lolokeksu09/Legion-Control` на 2026-11-21. Идентификатор Android-пакета: `dev.lolokeksu.legioncontrol` (см. [app Gradle](app/build.gradle.kts)). Владелец утвердил отдельное Android-приложение, Kotlin + Rust и приоритет работающего backend перед дизайном. Этот документ не заменяет актуальный код, манифест и CI.

## Карточка проекта и решения владельца

| Поле | Подтверждённое решение / источник |
|---|---|
| Исходная цель и пользователь | Системный и игровой центр для Lenovo Legion Y70 с Motorola-прошивкой; личное использование и тестирование владельцем. Новый проект отделён от `LenovoMotorolovichY70`. |
| Первая работающая версия | `0.1.0-alpha.1`: пользователь запускает Android-приложение, нажимает «Обновить измерение»; Kotlin читает батарею и режим дисплея, Rust через JNI записывает образец в 64-элементный буфер и рассчитывает изменения. См. [контракт backend](docs/BACKEND.md). |
| Наблюдаемый критерий готовности | Реальные Android-показания, изменяющийся при пользовательских запросах счётчик Rust, валидированные дельты, честная ошибка при невозможности JNI/чтения, успешная Rust + Android CI сборка. Физический запуск отдельно от CI. |
| Что пока не входит в объём | Root, Shizuku, скрытые OEM API, прямое управление Bypass/Touch Boost, thermal-модификации, запись FPS, телеметрия, улучшения UI «для вида». Удалённое управление указателем мыши не возвращать. |
| Поддерживаемые платформы и UX | Первичное устройство — Lenovo Legion Y70 с прошивкой Motorola. Android параметры сверять с [Gradle](app/build.gradle.kts) и [манифестом](app/src/main/AndroidManifest.xml); принципы — реальность данных, AMOLED, понятные ошибки. |
| Стек | Kotlin + Android SDK + Gradle для интерфейса/Android API; Rust + Cargo для native backend; JNI для связи. [Исходники Rust](rust-core/src/lib.rs), [проверка JNI](scripts/check_backend.py). |
| Критические сценарии | Нет подделки результатов Rust, истории не более 64 образцов, нет фонового мониторинга без явного запроса, нет опасных разрешений, источник данных проверен. |
| Результат и сохранение | CI по PR собирает Debug APK; выпуск с закреплённым PKCS12 на доверенном main (push либо ручной запуск) требует четырёх секретов GitHub Actions. Приватный ключ хранится исключительно вне репозитория. |
| Разрешённые внешние действия | Разработка, тестирование и изменение данного репозитория в границах задачи. Merge/выпуск и изменение доступов — только в рамках подтверждённого разрешения; задача на документальный шаблон не разрешает удаление веток, данных или релиз. |
| Запрещённые действия | Не изменять старый `Lenovo-Y70`, не включать непроверенные привилегированные переключатели, не делать сетевых отправок или опасных системных записей как побочный эффект. |

## Указатели на источники

- Цель и текущая технология: [README](README.md), [backend/ABI](docs/BACKEND.md), [Kotlin Activity](app/src/main/java/dev/lolokeksu/legioncontrol/MainActivity.kt), [Rust source](rust-core/src/lib.rs).
- Реальная конфигурация сборки: [корневой Gradle](build.gradle.kts), [app Gradle](app/build.gradle.kts), [Android CI](.github/workflows/android.yml), [Rust сборка](scripts/build-native.sh).
- Проверка и безопасность: [backend contract](scripts/check_backend.py), [APK verifier](scripts/verify_apk.py), [уровни](TESTING-LEVELS.md).
- Шаблоны и нормативный архив в полном объёме: [Project Workflow Standard v1.0](workflow-standard/README.md), [описание применения](docs/WORKFLOW-ADOPTION.md).
- Для задачи небольшой правки использовать Light. Для зависимых работ — рабочую копию [PLAN](workflow-standard/templates/PLAN.md), при необходимости [CONTINUATION](workflow-standard/templates/CONTINUATION.md). Исходные шаблоны не являются активным планом.

## Открытые решения

- Постоянный PKCS12-ключ создан и публичный отпечаток закреплён; CI для ручной релизной подписи подготовлен. GitHub Actions secrets ещё должен установить владелец; подписанный этим ключом release APK не заявлен до прохождения соответствующего workflow. См. [docs/RELEASE-SIGNING.md](docs/RELEASE-SIGNING.md).
- Дополнительные OEM функции и Shizuku требуют отдельного аппаратного аудита и согласования рисков; не считать их уже реализованными.
- Для физического Lenovo Y70 требуется проверка каждой новой версии отдельно. Скриншот из диалога подтверждает отображение первого экрана, но не служит автоматическим доказательством всех сценариев или CI.


## Следующий backend-инкремент

Версия `0.1.0-alpha.2` добавляет read-only Rust JNI статистику min/max/mean температуры батареи по локальному окну до 64 пользовательских замеров. Подписанный постоянным ключом APK возможен только после настройки секретов владельцем. Изменения UI ограничены добавлением строки фактической статистики.


## Реализация alpha.3 (2026-11-21)

Следующий проверяемый backend-сценарий: пользователь запускает измерение, видит публичные данные Android Power API и реальную длительность нативного окна, может отдельно поделиться своим отчётом через системный chooser или по подтверждению очистить только 64 записи Rust в RAM. Реальная постоянная подпись не перевыпускается, сертификат закреплён в [RELEASE-SIGNING](docs/RELEASE-SIGNING.md); ручной выпуск требует секретов владельца. Kotlin+Rust без Root/Shizuku, без управления устройством.

## Alpha.4 — состояние

Добавлена **долговременная SQLite история** ручных Kotlin/Rust измерений в приватной базе приложения, потоковый экспорт CSV через `ACTION_CREATE_DOCUMENT`, обзор предыдущих сеансов и проверка доступных через публичный `SensorManager` датчиков. Исправлены русские формы «измерение/измерения/измерений». Новый `versionCode=4`, подпись всё тем же приватным ключом и строгой проверкой публичного отпечатка, без изменений идентификатора пакета. Подробнее [docs/HISTORY.md](docs/HISTORY.md). Native Rust по-прежнему хранит максимум 64 образца в памяти и не требует Root/Shizuku.


## Постоянное решение владельца — 2026-11-21: backend не должен оставаться мёртвым кодом

Продолжаем **исключительно backend**: без переделки frontend/UX, без Shizuku/Root до отдельной задачи. Каждый новый backend-инкремент должен иметь фактическую точку вызова из приложения (сейчас: существующее ручное действие `MainActivity.sample()`), проверку реально отображаемого/экспортируемого результата и отказов Android API. После успешного CI и разрешённой проверки изменения переносить в **`main`**, а не оставлять функциональность только в черновых PR. Сборки с тем же постоянным ключом должны иметь увеличивающийся `versionCode` и не повреждать SQLite-историю.


## Backend-only alpha.6: device identity and reliability

The physical test device is described by its owner as **Legion Y70 (2026)**; the current firmware exposes a Motorola software identity. Never infer physical battery specifications from `Build.MODEL` or hardcode an earlier Y70's nominal capacity. Continue to use only verified Android API measurements, use explicit unavailable states and decode thermal status without claiming raw CPU/GPU thermometry. All new backend functionality must run in the existing manual sample/share scenario, pass CI, then merge into `main`, keeping the same `dev.lolokeksu.legioncontrol` package ID, signing certificate and SQLite schema.

## Alpha.7 — durability without destructive migration

The owner's next backend-only requirement includes longer multi-sample charging/thermal trends and lossless storage of actual diagnostic API values. Implemented in an additional SQLite table (v1→v2), with real SQLite migration tests and the same manual sample/share entry point. The installed release signing identity and app ID remain unchanged; unverified sustained-performance state in another process must stay **unknown**. No permission, Root, Shizuku, UI redesign or nominal capacity constant.

## Alpha.8 — функционал прежде редизайна, без Shizuku

Следующее утверждённое направление: сначала добавить реально работающий функционал, затем исправлять его по реальным аппаратным проверкам. Новая функция: расширенный экспорт CSV из фактической SQLite-диагностики v2, без потери данных v1 и без новой кнопки. Пользователь инициирует экспорт штатным системным выбором файла. Не добавлять Shizuku, Root, скрытые API или UI-редизайн. После CI слить в `main` и собрать APK прежним постоянным ключом.

## Alpha.9 — next real backend capability before design

Owner continues requesting new implemented functions before refinement, **without Shizuku**, Root or frontend changes. Deliver public Android battery health / charging metadata (Android `EXTRA_HEALTH`, API34+ cycles and charge status, technology, battery presence/low) and cautiously labeled battery-terminal electrical power. Integrate with the existing manual sample and share report; keep state genuinely unavailable when OEM hides it. Android categorical `GOOD` must not be converted into a fictional degradation percentage; cycle count must not be interpreted as full capacity. Preserve package ID, pinned release signer and SQLite v2; merge into main only after full CI, then verify signed release.


## Alpha.10 — backend first, new real functions (2026-11-22)

One-shot, user-triggered SensorManager capture (accelerometer, gyroscope, light, proximity), no sampling on startup or in the background. Validate timestamps, accuracy, finite values and dimensions; display missing/registration failure/timeout separately. Commit accepted events to SQLite v3 in a separate table, retaining legacy v1/v2 battery/diagnostic rows; show previous readings across app restarts. The existing 20-column battery CSV and Rust/JNI ABI remain unchanged. Two minimal buttons connect the functionality; no broader UI redesign. All sensor listeners unregister after 3 seconds, capture completion or Activity stop. Physical-device acceptance is separate from CI.


## Alpha.11 — большой backend-first патч (2026-11-22)

Цель: исправить недостаточную диагностику события освещённости в alpha.10 без обещания отсутствующих OEM-сигналов. Backend фиксирует timestamp, число полученных и отвергнутых SensorEvent, причину фильтрации, режим датчика и latency. Свету предоставляется один дополнительный 3-секундный интервал; остальные датчики завершаются за 3 с. События не берутся из кэша и не измеряются в фоне. Все завершённые результаты, включая отсутствие событий, атомарно сохраняются в новой SQLite таблице с обновлением v1/v2/v3→v4 без удаления старых данных. Тепловая история использует уже сохранённые ручные диагностические значения, а не создаёт периодическую телеметрию. Прежние 20-колоночный CSV, Rust JNI, разрешения и постоянный сертификат не меняются. Разработку ведём: сначала новые реально работающие backend-функции, потом совместимость/тесты, лишь затем визуал.


## Alpha.12 — backend-first extension (2026-11-22)

New functionality: bounded analytics from saved real sensor attempts and battery session readings. No inferred sampling rate, FPS or long-term battery prognosis. Stream a complete user-requested diagnostics ZIP containing original battery CSV, all sensor outcomes, real values and thermal history. No new Android permission, network, background monitoring or SQLite migration (v4 unchanged). All DB reads/exports and legacy battery storage execute on the existing single executor; the main battery insertion precedes the FK-linked diagnostic insertion and Rust session close. Verify with new JVM/SQL tests and existing full CI plus physical Lenovo Y70 acceptance.


## Alpha.13 — bounded battery test, backend first (2026-11-22)

Owner priority remains new functional backend → tests → minimal UI. A foreground-only, user-started 60-second Android+Rust battery sampling test (10-second target interval) records genuine sample values into a separate SQLite session. It ends on user stop, Activity onStop, Android SEVERE thermal status or battery temperature ≥45 C, or three failed reads; no background service, wakelock, Root, Shizuku or OEM controls. Rust's old in-memory buffer is deliberately NOT reset; the test summary is computed only from its own recorded points. The additive SQLite v5 timed_battery_tests table stores completed run metadata and actual committed row counts. All SQLite v1–v4 rows are preserved. Extend the manual ZIP with timed-tests.csv and keep battery.csv unchanged. Distinguish an accepted Rust sample from a successfully persisted SQLite sample, and never treat missing thermal status as 0. Physical Lenovo Y70 acceptance remains necessary after all mandatory CI gates.


## Alpha.14 — opt-in Gaming Lab, backend first (2026-11-22)

Ten approved public API capabilities in an isolated GamingDiagnosticsActivity with one entry button on MainActivity: TCP/DNS statistics (not ICMP/game-server ping), battery counters, thermal status callback/headroom, View-owned touch events, in-app Choreographer pacing (not other app FPS), active network/Wi-Fi state (may be redacted), opted-in explicit package UsageEvents, battery-terminal current/voltage comparison, existing SQLite session comparison, RAM/storage/own-process system health. No schema migration, background service or changed JNI contracts.

Normal permissions: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE; optional special PACKAGE_USAGE_STATS via settings/AppOps only. No ACCESS_FINE_LOCATION, READ_PHONE_STATE, QUERY_ALL_PACKAGES, Root, Shizuku, overlay, Accessibility, hidden OEM control or background collection. User-triggered TCP target is one.one.one.one:443, no proprietary analytics endpoint or cloud reporting. UI stops active callback/dialog/work when leaving. Verify exact permission allowlist, real parameterized SQLite, Kotlin unit tests, old Rust/Android signing gates and a physical Lenovo Y70 10-feature acceptance suite. User priority is new backend/functions first, then tests, then visual polish.


## Alpha.15 — new public-API Hardware Lab (2026-11-22)

Owner requested another large, functionality-first patch without Root/Shizuku. Implement six self-contained new opt-in Android diagnostics in HardwareLabActivity reached from GamingDiagnosticsActivity: current LinkProperties route/DNS inspector, bounded 30-second registerDefaultNetworkCallback events with UI/lifecycle cancellation, 8 MiB cache-only SHA-256 validated write+fsync/read benchmark with finally cleanup, bounded 5-second accelerometer/gyro SensorManager capture with absence/invalid handling, manual fullscreen solid-color display inspection without global setting change, and MediaCodecList hardware decoder capability inventory. Add user-initiated system share of last report; never upload diagnostics.

Android manifest retains exactly the alpha.14 four permissions, no location, Root, Shizuku or privileged service. No SQLite schema migration (v5 remains), no Rust JNI change, no new background service, no fake device or game FPS. Tests: pure Kotlin numeric edge cases, Python exact user actions, timeout/unregister and permissions contracts, existing native/Android/pinned-signing CI, then separate physical Lenovo Y70 smoke test. Implementation order remains backend → functions → tests → UI polish.


## Alpha.16 — public Android compatibility and readiness (2026-11-22)

Owner requested another substantial backend-first patch. Add seven opt-in tools in CompatibilityLabActivity reached from HardwareLabActivity: Display mode/HDR catalog, AudioManager output and reported PCM settings, InputDevice gamepad inventory, focused GamepadLiveActivity real key/axis events, MediaDrm Widevine support/properties with immediate close and no license, GamingReadinessSnapshot with seven rule-based OK/CAUTION/CRITICAL/UNKNOWN checks, bounded 500k-step integer compute probe with thermal stop policy. Provide explicit offline full report and user-initiated ACTION_SEND; never upload.

Preserve SQLite v5, all existing battery/sensor history, native Rust JNI, application ID, previous permissions, 20-column CSV and pinned release signer. No Root/Shizuku/accessibility/global input capture. Unknown ROM properties remain unknown. A passing CI build is not a physical test. Verify pure JVM readiness and compute, static Android API/permission guard, old Kotlin/Rust/SQLite tests and signed APK certificate; run device smoke test separately.

## Архитектура приложения — решение владельца для alpha.26 (2026-11-22)

Владелец запросил полноценную навигационную архитектуру (MVVM) **с сохранением рабочего бэкенда**. Новый `NavigationHostActivity` с AndroidX `NavHostFragment` и пятью основными вкладками используется как единственный публичный Launcher. Граф `app/src/main/res/navigation/legion_nav_graph.xml`, View `HubPageFragment` и `GameDetailFragment`, `HubViewModel` (LiveData, единый worker), `HubRepository` (существующие SQLite-репозитории, системный Launcher). Приоритет: реальные пользовательские действия и сохранение данных, не косметическая замена кнопок.

**Контракт совместимости:** исходный `MainActivity` сохранён как неэкспортируемый экран полной диагностики Kotlin/Rust (переход из «Инструменты» и «История»). Все старые лаборатории, сервисы и JNI не переносились, не заменялись заглушками; прежняя SQLite v12, id `dev.lolokeksu.legioncontrol`, системные разрешения и постоянная подпись не меняются. Таким образом новая оболочка использует MVVM, но старые модули не объявляются уже мигрировавшими. См. [план](docs/ALPHA26-MVVM-NAVIGATION-PLAN.md), [руководство](docs/ALPHA26-MVVM-NAVIGATION.md), [тест](scripts/check_alpha26_mvvm_navigation.py).

## Alpha.27 — MVVM backends for diagnostics and Capture Studio (2026-11-22)

User authorized continuing the full architecture refactor from alpha.26. This stage extracts read-only diagnostics history and capture presets/MediaStore library into `DiagnosticsHistoryRepository`/`DiagnosticsHistoryViewModel` and `CaptureStudioRepository`/`CaptureStudioViewModel`. Activities retain native Rust sampling/safe SQLite write order and fresh MediaProjection consent: ViewModels do not own security tokens or uncontrolled lifecycle requests. `MainActivity` and `CaptureStudioActivity` now use `ComponentActivity` and lifecycle observers. Manifest permission allowlist, SQLite v12 and signer unchanged. All other legacy labs remain reachable pending their own backend-boundary migrations. [Plan](docs/ALPHA27-MVVM-BACKEND-REFACTOR-PLAN.md) / [design and test](docs/ALPHA27-MVVM-BACKEND-REFACTOR.md).


## Alpha.49 — unified application presentation and release (2026-11-24)

Version `0.1.0-alpha.49` (`versionCode=49`) is the approved follow-up to Alpha.48. It consolidates 66 app-owned dialog call sites across 19 screens, chart axes/empty states, compact report cards and semantic loading/error/empty notices. The five-tab navigation retains full accessible names; the visible Tools label is shortened to prevent line wrapping. The public-API telemetry, Java/Kotlin/Rust boundaries, SQLite schema v12, installed-data continuity, package `dev.lolokeksu.legioncontrol`, permission manifest and permanent pinned signing certificate remain unchanged. See [UI changes](docs/ALPHA49-UNIFIED-UI.md) and [release notes](docs/ALPHA49-RELEASE-NOTES.md). Publish only on successful exact-main Android + signed workflow. Emulator checks do not substitute for physical-device L5 testing.


## 2026-11-26 — approved Shizuku and fast integration policy (supersedes older restrictions)

Owner explicitly authorizes optional Shizuku, large autonomous improvements, merge to main and signed APK assembly without repeated approvals. Earlier blanket prohibition on Shizuku is superseded ONLY for the optional official SDK boundary; ordinary Android functionality remains usable with no service or permission. No root operations, arbitrary privileged shell, hidden OEM controls or unsolicited permission grants. Initial integration: user-requested permission, binder lifecycle, ADB UID detection and fixed read-only remote permission probes. Additional privileged mutations require an explicit in-app user action and a defined bounded API/rollback, not a generic command interface. Never change app ID, pinned signer, SQLite v12 or stored user data without separate migration authorization.

Owner requests skipping large tests: full Android 35 emulator job remains available via manual workflow_dispatch full_emulator=true; automatic merge/build uses source/SQLite checks, JVM unit tests, Rust, compile/lint, native APK and certificate/dependency gates. Mark emulator NOT RUN when skipped; do not claim L4 full device acceptance. Main integration and build-only artifact are authorized; public release publication remains a separate explicit choice. See [consolidation audit](docs/AUDIT-SHIZUKU-CONSOLIDATION-2026-11-26.md).
