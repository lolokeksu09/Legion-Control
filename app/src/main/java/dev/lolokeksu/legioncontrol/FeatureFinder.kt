package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Stable identifiers for real visible controls; no executable action is stored here. */
enum class FeatureModule(val displayName: String) {
    CAPTURE("Capture Studio"), AUDIO("Audio Lab"), HAPTIC("Haptic Lab"),
    NETWORK("Connectivity Observatory"), PROFILE("Smart Profiles"),
    APPS("Application Center"), SESSION("Game Session"), TOOLBOX("Game Toolbox"),
    GAMING("Gaming Lab"),
    HARDWARE("Hardware Lab"),
    COMPAT("Compatibility Lab"),
    EXPERIMENT("Experiment Lab"),
    SIGNALS("Session Signals"),
    INCIDENTS("Session Incidents"),
    BASELINES("Session Baselines"),
    DIAGNOSTICS("Диагностика Kotlin + Rust"),
    GAMEPAD("Gamepad Live"),
    MONITOR("Live Monitor"),
    PREFLIGHT("Game Preflight"),
    COMPARE("Session Compare"),
    LIBRARY("Session Library")
}

data class FeatureEntry(
    val id: String,
    val module: FeatureModule,
    val title: String,
    val anchor: String,
    val keywords: String
)

/**
 * A catalog result opens a module and reveals an existing control.
 * It must never execute that control, grant permission, or start a service.
 */
object FeatureFinder {
    const val EXTRA_FEATURE_ID = "legion_feature_id"

    val entries = listOf(
        FeatureEntry("capture.record", FeatureModule.CAPTURE, "Запись экрана", "НАЧАТЬ ЗАПИСЬ ЭКРАНА", "mp4 видео screen recording"),
        FeatureEntry("capture.replay", FeatureModule.CAPTURE, "Мгновенный повтор", "ЗАПУСТИТЬ REPLAY-БУФЕР", "instant replay последние секунды буфер"),
        FeatureEntry("capture.screenshot", FeatureModule.CAPTURE, "Скриншот экрана", "СДЕЛАТЬ СКРИНШОТ ЭКРАНА", "png screenshot снимок"),
        FeatureEntry("capture.quality", FeatureModule.CAPTURE, "Разрешение записи", "Разрешение · 720p / 1080p", "качество 720p 1080p"),
        FeatureEntry("capture.audio", FeatureModule.CAPTURE, "Звук игры в записи", "Звук игры · включить / выключить", "aac playback audio внутренний звук"),
        FeatureEntry("capture.clips", FeatureModule.CAPTURE, "Библиотека клипов MP4", "ОТКРЫТЬ БИБЛИОТЕКУ КЛИПОВ", "clip workshop видео"),
        FeatureEntry("capture.backup", FeatureModule.CAPTURE, "Резервная копия ZIP", "ЭКСПОРТ ИСТОРИИ И НАСТРОЕК", "backup экспорт sqlite архив"),
        FeatureEntry("capture.restore", FeatureModule.CAPTURE, "Восстановление из ZIP", "ПРОВЕРИТЬ И ВОССТАНОВИТЬ ИЗ ZIP", "restore импорт backup sqlite"),
        FeatureEntry("capture.resume", FeatureModule.CAPTURE, "Завершить прерванный импорт", "ЗАВЕРШИТЬ ПРЕРВАННОЕ ВОССТАНОВЛЕНИЕ", "restore журнал восстановление zip"),
        FeatureEntry("audio.stereo", FeatureModule.AUDIO, "Проверка стерео", "Стерео · 440 Гц", "sound audio звук stereo динамики"),
        FeatureEntry("audio.sweep", FeatureModule.AUDIO, "Проверка частот", "Частотный sweep: 220 → 1760 Гц", "audio частота sweep hz"),
        FeatureEntry("audio.routes", FeatureModule.AUDIO, "Подключённые аудиовыходы", "Обновить подключённые аудиовыходы", "audio bluetooth наушники маршруты"),
        FeatureEntry("haptic.test", FeatureModule.HAPTIC, "Проверка вибрации", "Быстрая проверка: один короткий импульс", "haptic vibration вибромотор"),
        FeatureEntry("haptic.capabilities", FeatureModule.HAPTIC, "Возможности вибромотора", "Проверить Android Vibra-capabilities", "haptic android amplitude"),
        FeatureEntry("network.single", FeatureModule.NETWORK, "Сетевой замер DNS/TCP/TLS", "ОДИН ЗАМЕР · DNS / IPv4 / IPv6 / TCP / TLS", "network latency соединение сеть"),
        FeatureEntry("network.stability", FeatureModule.NETWORK, "Стабильность сети", "ТЕСТ СТАБИЛЬНОСТИ · до 6 циклов (~60 секунд)", "network jitter стабильность интернет"),
        FeatureEntry("network.history", FeatureModule.NETWORK, "История сетевых тестов", "История проверок · выбрать запись", "network история dns tcp"),
        FeatureEntry("profile.rules", FeatureModule.PROFILE, "Правила игрового профиля", "Настроить 6 правил профиля", "smart profile температура батареи порог"),
        FeatureEntry("profile.brightness", FeatureModule.PROFILE, "Яркость экрана", "Яркость 50% (с сохранением исходного значения)", "brightness экран яркость"),
        FeatureEntry("profile.export", FeatureModule.PROFILE, "Экспорт профилей JSON", "Экспортировать профили и правила JSON", "smart profile backup json"),
        FeatureEntry("apps.permissions", FeatureModule.APPS, "Разрешения приложения", "Карточка приложения и видимые разрешения", "app permissions права"),
        FeatureEntry("apps.usage", FeatureModule.APPS, "Использование приложений", "Использование приложения за 24 часа", "usage access статистика время"),
        FeatureEntry("session.mark", FeatureModule.SESSION, "Метка игровой сессии", "Добавить метку во время записи", "session marker телеметрия"),
        FeatureEntry("session.csv", FeatureModule.SESSION, "Экспорт игровой истории", "Сохранить историю замеров CSV", "session csv история"),
        FeatureEntry("toolbox.profile", FeatureModule.TOOLBOX, "Профиль запуска игры", "Настроить профиль", "game toolbox яркость громкость dnd"),
        FeatureEntry("toolbox.undo", FeatureModule.TOOLBOX, "Возврат системных настроек", "Восстановить прежние настройки", "game toolbox undo rollback яркость"),
        FeatureEntry("gaming.network", FeatureModule.GAMING, "Анализ сети игры", "1 · Network Analyzer — TCP/DNS", "сеть tcp dns network latency"),
        FeatureEntry("gaming.wifi", FeatureModule.GAMING, "Состояние Wi-Fi и LTE", "6 · Wi-Fi / LTE Inspector", "wifi lte мобильная сеть сигнал"),
        FeatureEntry("gaming.battery", FeatureModule.GAMING, "Диагностика батареи", "2 · Battery Intelligence", "battery заряд аккумулятор"),
        FeatureEntry("gaming.thermal", FeatureModule.GAMING, "Тепловые события", "3 · Thermal Guard — включить/выключить события", "thermal нагрев температура троттлинг"),
        FeatureEntry("gaming.touch", FeatureModule.GAMING, "Проверка мультитача", "4 · Touch Analyzer — мультитач и края", "touch экран касания сенсор"),
        FeatureEntry("gaming.frames", FeatureModule.GAMING, "Интервалы кадров интерфейса", "5 · Frame Pacing — 5 секунд", "frame pacing плавность кадры интерфейс"),
        FeatureEntry("gaming.charging", FeatureModule.GAMING, "Анализ зарядки", "8 · Charging Analyzer — сравнить с прошлым замером", "charging зарядка аккумулятор"),
        FeatureEntry("gaming.health", FeatureModule.GAMING, "Состояние системы", "10 · System Health", "system health память состояние"),
        FeatureEntry("hardware.route", FeatureModule.HARDWARE, "Маршруты локальной сети", "1 · Local Route Inspector", "route шлюз локальная сеть"),
        FeatureEntry("hardware.events", FeatureModule.HARDWARE, "События подключения", "2 · Network Events — 30 секунд", "network events соединение"),
        FeatureEntry("hardware.storage", FeatureModule.HARDWARE, "Проверка накопителя", "3 · Storage Lab — 8 МиБ / SHA-256", "storage диск запись чтение память sha256"),
        FeatureEntry("hardware.motion", FeatureModule.HARDWARE, "Стабильность датчиков движения", "4 · Motion Stability — 5 секунд", "motion акселерометр гироскоп"),
        FeatureEntry("hardware.display", FeatureModule.HARDWARE, "Тест цветов и пикселей", "5 · Display Patterns — пиксели и цвета", "display экран битые пиксели oled amoled"),
        FeatureEntry("hardware.codec", FeatureModule.HARDWARE, "Аппаратные видеодекодеры", "6 · Codec Inspector — аппаратные видеодекодеры", "codec видео декодер"),
        FeatureEntry("compat.hdr", FeatureModule.COMPAT, "Режимы экрана и HDR", "1 · Display Modes & HDR", "display hdr частота обновления"),
        FeatureEntry("compat.audio", FeatureModule.COMPAT, "Аудиовыходы Android", "2 · Audio Output Inspector", "audio output звук"),
        FeatureEntry("compat.controllers", FeatureModule.COMPAT, "Список игровых контроллеров", "3 · Gamepad Inventory", "gamepad геймпад bluetooth usb контроллер"),
        FeatureEntry("compat.drm", FeatureModule.COMPAT, "Совместимость Widevine", "5 · DRM / Widevine Compatibility", "drm widevine защищенное видео"),
        FeatureEntry("compat.cpu", FeatureModule.COMPAT, "Локальный тест CPU", "7 · Quick Compute Probe · локальный CPU-тест", "cpu процессор производительность"),
        FeatureEntry("compat.report", FeatureModule.COMPAT, "Отчёт совместимости", "Полный отчёт о совместимости", "android compatibility отчёт"),
        FeatureEntry("experiment.create", FeatureModule.EXPERIMENT, "Создать A/B эксперимент", "Создать эксперимент", "experiment сравнение условия"),
        FeatureEntry("experiment.compare", FeatureModule.EXPERIMENT, "Сравнить результаты A/B", "Показать сравнение A/B", "experiment сравнение ab"),
        FeatureEntry("experiment.csv", FeatureModule.EXPERIMENT, "Экспорт эксперимента CSV", "Экспортировать A/B данные CSV", "experiment export отчет"),
        FeatureEntry("signals.timeline", FeatureModule.SIGNALS, "Хронология игровой сессии", "Обновить хронологию", "session signals события метки"),
        FeatureEntry("signals.csv", FeatureModule.SIGNALS, "Экспорт событий сессии", "Сохранить события выбранной сессии CSV", "session signals csv история"),
        FeatureEntry("incidents.filter", FeatureModule.INCIDENTS, "Фильтр проблемных состояний", "Фильтр категорий", "session incidents эпизоды ошибки"),
        FeatureEntry("incidents.csv", FeatureModule.INCIDENTS, "Экспорт эпизодов CSV", "Сохранить эпизоды CSV", "session incidents отчет"),
        FeatureEntry("baselines.week", FeatureModule.BASELINES, "Базовые показатели за неделю", "За последние 7 дней", "session baseline сравнение 7 дней"),
        FeatureEntry("baselines.month", FeatureModule.BASELINES, "Базовые показатели за месяц", "За последние 30 дней", "session baseline сравнение 30 дней"),
        FeatureEntry("baselines.csv", FeatureModule.BASELINES, "Экспорт базовых показателей", "Экспортировать сравнение CSV", "session baseline csv отчет"),
        FeatureEntry("diag.measure", FeatureModule.DIAGNOSTICS, "Замер устройства Kotlin/Rust", "Обновить измерение", "rust jni измерение батареи"),
        FeatureEntry("diag.battery", FeatureModule.DIAGNOSTICS, "Тест батареи 60 секунд", "Тест батареи · 60 секунд", "battery расход заряд"),
        FeatureEntry("diag.thermal", FeatureModule.DIAGNOSTICS, "Тепловая история", "Тепловая история", "thermal температура история"),
        FeatureEntry("diag.sensors", FeatureModule.DIAGNOSTICS, "Показания датчиков", "Снять показания датчиков", "sensor сенсор акселерометр"),
        FeatureEntry("diag.zip", FeatureModule.DIAGNOSTICS, "Архив полной диагностики", "Экспорт полной диагностики ZIP", "rust diagnostic zip экспорт"),
        FeatureEntry("gamepad.refresh", FeatureModule.GAMEPAD, "Обновить контроллеры", "Обновить список контроллеров", "gamepad bluetooth usb геймпад"),
        FeatureEntry("monitor.start", FeatureModule.MONITOR, "Живой монитор устройства", "НАЧАТЬ МОНИТОРИНГ", "live monitor ram температура заряд частота дисплея"),
        FeatureEntry("monitor.chart", FeatureModule.MONITOR, "График показаний", "Выбрать показатель графика", "live monitor graph ram температура"),
        FeatureEntry("monitor.baseline", FeatureModule.MONITOR, "Контрольная точка мониторинга", "ЗАФИКСИРОВАТЬ КОНТРОЛЬНУЮ ТОЧКУ", "live monitor baseline сравнение"),
        FeatureEntry("monitor.csv", FeatureModule.MONITOR, "Экспорт мониторинга CSV", "СОХРАНИТЬ МОНИТОРИНГ CSV", "live monitor export csv"),
        FeatureEntry("preflight.game", FeatureModule.PREFLIGHT, "Выбор игры для предпусковой проверки", "ВЫБРАТЬ ИГРУ ДЛЯ ПРОВЕРКИ", "game preflight игра запуск проверка"),
        FeatureEntry("preflight.requirements", FeatureModule.PREFLIGHT, "Требования конкретной игры", "НАСТРОИТЬ ТРЕБОВАНИЯ ИГРЫ", "preflight target fps hz требования ram батарея"),
        FeatureEntry("preflight.check", FeatureModule.PREFLIGHT, "Проверка перед запуском игры", "ВЫПОЛНИТЬ ПРОВЕРКУ ПЕРЕД ЗАПУСКОМ", "game preflight энергосбережение дисплей температура сеть"),
        FeatureEntry("preflight.report", FeatureModule.PREFLIGHT, "Поделиться результатами предпусковой проверки", "Поделиться отчётом проверки", "preflight report отчет"),
        FeatureEntry("compare.game", FeatureModule.COMPARE, "Выбрать игру для сравнения сессий", "ВЫБРАТЬ ИГРУ ИЗ ИСТОРИИ", "session compare история игры сравнение"),
        FeatureEntry("compare.window", FeatureModule.COMPARE, "Сравнение одинаковых временных окон", "Выбрать окно сравнения", "session compare 5 10 15 30 минут"),
        FeatureEntry("compare.run", FeatureModule.COMPARE, "Сравнить выбранные записи", "СРАВНИТЬ ВЫБРАННЫЕ СЕССИИ", "session compare заряд нагрев сеть дисплей"),
        FeatureEntry("compare.graph", FeatureModule.COMPARE, "Совмещённый график сессий", "Выбрать показатель сравнения", "session compare график температура заряд герцы"),
        FeatureEntry("compare.csv", FeatureModule.COMPARE, "Экспорт сравнения двух сессий CSV", "СОХРАНИТЬ СРАВНЕНИЕ CSV", "session compare экспорт csv"),
        FeatureEntry("library.game", FeatureModule.LIBRARY, "Фильтрация истории по игре", "Фильтр по игре", "session library игры история"),
        FeatureEntry("library.period", FeatureModule.LIBRARY, "Фильтрация записей по периоду", "Фильтр по периоду", "session library дата 7 30 90"),
        FeatureEntry("library.state", FeatureModule.LIBRARY, "Фильтрация завершённых и прерванных сессий", "Фильтр по завершению", "session library статус"),
        FeatureEntry("library.zip", FeatureModule.LIBRARY, "Архив одной игровой сессии", "ЭКСПОРТ ВЫБРАННОЙ СЕССИИ ZIP", "session library zip экспорт телеметрия события")
    )

    fun resolve(id: String?, module: FeatureModule): FeatureEntry? =
        entries.firstOrNull { it.id == id && it.module == module }

    private fun normalized(text: String): String =
        text.lowercase(Locale.ROOT).replace('ё', 'е').trim()

    fun matches(entry: FeatureEntry, query: String): Boolean {
        val words = normalized(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val searchable = normalized("${entry.title} ${entry.anchor} ${entry.module.displayName} ${entry.keywords}")
        return words.all { searchable.contains(it) }
    }

    fun search(query: String): List<FeatureEntry> = entries.filter { matches(it, query) }
}
