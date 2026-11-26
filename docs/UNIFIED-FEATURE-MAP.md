# Unified Architecture — реестр стабильных действий

База 7b9091f; фактические ID и названия из FeatureFinder.kt. Центр — целевой владелец пользовательского пути, экран — первичный маршрут после первого среза A. Существующие специализированные backend и legacy маршруты сохранены. Для остальных центров это карта миграции, не утверждение об их реализации.

| ID | Модуль | Действие | Центр | Экран |
|---|---|---|---|---|
| `capture.record` | CAPTURE | Запись экрана | Медиацентр | `CaptureStudioActivity` |
| `capture.replay` | CAPTURE | Мгновенный повтор | Медиацентр | `CaptureStudioActivity` |
| `capture.screenshot` | CAPTURE | Скриншот экрана | Медиацентр | `CaptureStudioActivity` |
| `capture.quality` | CAPTURE | Разрешение записи | Медиацентр | `CaptureStudioActivity` |
| `capture.audio` | CAPTURE | Звук игры в записи | Медиацентр | `CaptureStudioActivity` |
| `capture.clips` | CAPTURE | Медиатека MP4 и PNG | Медиацентр | `CaptureStudioActivity` |
| `capture.backup` | CAPTURE | Резервная копия ZIP | Медиацентр | `CaptureStudioActivity` |
| `capture.restore` | CAPTURE | Восстановление из ZIP | Медиацентр | `CaptureStudioActivity` |
| `capture.resume` | CAPTURE | Завершить прерванный импорт | Медиацентр | `CaptureStudioActivity` |
| `audio.stereo` | AUDIO | Проверка стерео | Периферия | `AudioLabFragment` |
| `audio.sweep` | AUDIO | Проверка частот | Периферия | `AudioLabFragment` |
| `audio.routes` | AUDIO | Подключённые аудиовыходы | Периферия | `AudioLabFragment` |
| `haptic.test` | HAPTIC | Проверка вибрации | Периферия | `HapticLabFragment` |
| `haptic.capabilities` | HAPTIC | Возможности вибромотора | Периферия | `HapticLabFragment` |
| `network.single` | NETWORK | Сетевой замер DNS/TCP/TLS | Устройство | `ConnectivityLabFragment` |
| `network.stability` | NETWORK | Стабильность сети | Устройство | `ConnectivityLabFragment` |
| `network.history` | NETWORK | История сетевых тестов | Устройство | `ConnectivityLabFragment` |
| `profile.rules` | PROFILE | Правила игрового профиля | Игровой центр | `SmartProfilesFragment` |
| `profile.brightness` | PROFILE | Системное управление | Игровой центр | `SmartProfilesFragment` |
| `profile.export` | PROFILE | Экспорт профилей JSON | Игровой центр | `SmartProfilesFragment` |
| `apps.permissions` | APPS | Разрешения приложения | Игровой центр / приложения | `ApplicationCenterFragment` |
| `apps.usage` | APPS | Использование приложений | Игровой центр / приложения | `ApplicationCenterFragment` |
| `session.mark` | SESSION | Метка игровой сессии | Игровой центр | `GameSessionFragment` |
| `session.csv` | SESSION | Экспорт игровой истории | Игровой центр | `GameSessionFragment` |
| `toolbox.profile` | TOOLBOX | Профиль запуска игры | Игровой центр | `GameToolboxFragment` |
| `toolbox.undo` | TOOLBOX | Возврат системных настроек | Игровой центр | `GameToolboxFragment` |
| `gaming.overview` | GAMING | Сводка устройства | Устройство | `GamingDiagnosticsFragment` |
| `gaming.network` | GAMING | Анализ сети игры | Устройство | `GamingDiagnosticsFragment` |
| `gaming.wifi` | GAMING | Состояние Wi-Fi и LTE | Устройство | `GamingDiagnosticsFragment` |
| `gaming.battery` | GAMING | Диагностика батареи | Устройство | `GamingDiagnosticsFragment` |
| `gaming.thermal` | GAMING | Тепловые события | Устройство | `GamingDiagnosticsFragment` |
| `gaming.touch` | GAMING | Проверка мультитача | Устройство | `GamingDiagnosticsFragment` |
| `gaming.frames` | GAMING | Интервалы кадров интерфейса | Устройство | `GamingDiagnosticsFragment` |
| `gaming.charging` | GAMING | Анализ зарядки | Устройство | `GamingDiagnosticsFragment` |
| `gaming.health` | GAMING | Состояние системы | Устройство | `GamingDiagnosticsFragment` |
| `hardware.route` | HARDWARE | Маршруты локальной сети | Устройство | `HardwareLabFragment` |
| `hardware.events` | HARDWARE | События подключения | Устройство | `HardwareLabFragment` |
| `hardware.storage` | HARDWARE | Проверка накопителя | Устройство | `HardwareLabFragment` |
| `hardware.motion` | HARDWARE | Стабильность датчиков движения | Устройство | `HardwareLabFragment` |
| `hardware.display` | HARDWARE | Тест цветов и пикселей | Устройство | `HardwareLabFragment` |
| `hardware.codec` | HARDWARE | Аппаратные видеодекодеры | Устройство | `HardwareLabFragment` |
| `compat.hdr` | COMPAT | Режимы экрана и HDR | Устройство / периферия | `CompatibilityLabFragment` |
| `compat.audio` | COMPAT | Аудиовыходы Android | Устройство / периферия | `CompatibilityLabFragment` |
| `compat.controllers` | COMPAT | Список игровых контроллеров | Устройство / периферия | `CompatibilityLabFragment` |
| `compat.drm` | COMPAT | Совместимость Widevine | Устройство / периферия | `CompatibilityLabFragment` |
| `compat.cpu` | COMPAT | Локальный тест CPU | Устройство / периферия | `CompatibilityLabFragment` |
| `compat.report` | COMPAT | Отчёт совместимости | Устройство / периферия | `CompatibilityLabFragment` |
| `experiment.create` | EXPERIMENT | Создать A/B эксперимент | Аналитика / A/B | `ExperimentLabFragment` |
| `experiment.compare` | EXPERIMENT | Сравнить результаты A/B | Аналитика / A/B | `ExperimentLabFragment` |
| `experiment.csv` | EXPERIMENT | Экспорт эксперимента CSV | Аналитика / A/B | `ExperimentLabFragment` |
| `signals.timeline` | SIGNALS | Хронология игровой сессии | Аналитика | `SessionLibraryActivity` |
| `signals.csv` | SIGNALS | Экспорт событий сессии | Аналитика | `SessionLibraryActivity` |
| `incidents.filter` | INCIDENTS | Фильтр проблемных состояний | Аналитика | `SessionLibraryActivity` |
| `incidents.csv` | INCIDENTS | Экспорт эпизодов CSV | Аналитика | `SessionLibraryActivity` |
| `baselines.week` | BASELINES | Базовые показатели за неделю | Аналитика | `SessionLibraryActivity` |
| `baselines.month` | BASELINES | Базовые показатели за месяц | Аналитика | `SessionLibraryActivity` |
| `baselines.csv` | BASELINES | Экспорт базовых показателей | Аналитика | `SessionLibraryActivity` |
| `diag.measure` | DIAGNOSTICS | Замер устройства Kotlin/Rust | Устройство | `MainActivity` |
| `diag.battery` | DIAGNOSTICS | Тест батареи 60 секунд | Устройство | `MainActivity` |
| `diag.thermal` | DIAGNOSTICS | Тепловая история | Устройство | `MainActivity` |
| `diag.sensors` | DIAGNOSTICS | Показания датчиков | Устройство | `MainActivity` |
| `diag.zip` | DIAGNOSTICS | Архив полной диагностики | Устройство | `MainActivity` |
| `gamepad.refresh` | GAMEPAD | Обновить контроллеры | Периферия | `GamepadLiveActivity` |
| `monitor.start` | MONITOR | Живой монитор устройства | Устройство | `MainActivity / LiveMonitorPanel` |
| `monitor.chart` | MONITOR | График показаний | Устройство | `MainActivity / LiveMonitorPanel` |
| `monitor.baseline` | MONITOR | Контрольная точка мониторинга | Устройство | `MainActivity / LiveMonitorPanel` |
| `monitor.csv` | MONITOR | Экспорт мониторинга CSV | Устройство | `MainActivity / LiveMonitorPanel` |
| `preflight.game` | PREFLIGHT | Выбор игры для предпусковой проверки | Игровой центр | `GamePreflightActivity` |
| `preflight.requirements` | PREFLIGHT | Требования конкретной игры | Игровой центр | `GamePreflightActivity` |
| `preflight.check` | PREFLIGHT | Проверка перед запуском игры | Игровой центр | `GamePreflightActivity` |
| `preflight.report` | PREFLIGHT | Поделиться результатами предпусковой проверки | Игровой центр | `GamePreflightActivity` |
| `compare.game` | COMPARE | Выбрать игру для сравнения сессий | Аналитика | `SessionLibraryActivity` |
| `compare.window` | COMPARE | Сравнение одинаковых временных окон | Аналитика | `SessionLibraryActivity` |
| `compare.run` | COMPARE | Сравнить выбранные записи | Аналитика | `SessionLibraryActivity` |
| `compare.graph` | COMPARE | Совмещённый график сессий | Аналитика | `SessionLibraryActivity` |
| `compare.csv` | COMPARE | Экспорт сравнения двух сессий CSV | Аналитика | `SessionLibraryActivity` |
| `library.game` | LIBRARY | Фильтрация истории по игре | Аналитика | `SessionLibraryActivity` |
| `library.period` | LIBRARY | Фильтрация записей по периоду | Аналитика | `SessionLibraryActivity` |
| `library.state` | LIBRARY | Фильтрация завершённых и прерванных сессий | Аналитика | `SessionLibraryActivity` |
| `library.zip` | LIBRARY | Архив одной игровой сессии | Аналитика | `SessionLibraryActivity` |
