# Особенности проекта — Legion Control

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
