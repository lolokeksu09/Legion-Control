package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Single browse registry. Stable module routes are still owned by HubDestinationRoutes. */
internal data class HubToolEntry(val title: String, val description: String, val activity: Class<*>)
internal data class HubToolGroup(val title: String, val entries: List<HubToolEntry>)

internal object HubToolCatalog {
    private fun item(title: String, detail: String, activity: Class<*>) =
        HubToolEntry(title, detail, activity)

    val groups = listOf(
        HubToolGroup("Игровое управление", listOf(
            item("Game Toolbox", "Панель, профиль запуска, возврат настроек", GameToolboxActivity::class.java),
            item("Smart Profiles", "Правила, аналитика и профили", SmartProfilesActivity::class.java),
            item("Application Center", "Приложения, избранное и разрешения", ApplicationCenterActivity::class.java),
            item("Игровая сессия", "Запись телеметрии и метки", GameSessionActivity::class.java),
            item("Game Preflight", "Предпусковая проверка выбранной игры", GamePreflightActivity::class.java)
        )),
        HubToolGroup("Запись и медиа", listOf(
            item("Capture Studio", "Запись, Replay, скриншоты и библиотека MP4", CaptureStudioActivity::class.java),
            item("Audio Lab", "Стерео, частоты и аудиомаршруты", AudioLabActivity::class.java)
        )),
        HubToolGroup("Экран и управление", listOf(
            item("Display Patterns", "Проверка дисплея", DisplayPatternActivity::class.java),
            item("Haptic Lab", "Вибрация и тактильная отдача", HapticLabActivity::class.java),
            item("Gamepad Live", "Кнопки и стики контроллера", GamepadLiveActivity::class.java)
        )),
        HubToolGroup("Система и диагностика", listOf(
            item("Gaming Lab", "Батарея, сеть, сенсоры и производительность", GamingDiagnosticsActivity::class.java),
            item("Hardware Lab", "Аппаратные измерения и тесты", HardwareLabActivity::class.java),
            item("Compatibility Lab", "Совместимость функций Android", CompatibilityLabActivity::class.java),
            item("Диагностический центр", "Kotlin + Rust, Live Monitor, батарея, RAM, датчики, история, графики и CSV/ZIP", MainActivity::class.java)
        )),
        HubToolGroup("Сеть", listOf(
            item("Connectivity Observatory", "DNS, IPv4/IPv6, TCP и TLS", ConnectivityLabActivity::class.java)
        )),
        HubToolGroup("История и сравнение", listOf(
            item("Experiment Lab", "A/B-сравнения и заметки", ExperimentLabActivity::class.java),
            item("Игровая аналитика", "Session Library, Compare, Signals, Incidents, Baselines, графики и CSV/ZIP", SessionLibraryActivity::class.java)
        ))
    )

    /** Word-by-word matches support combinations such as "game профиль". */
    fun matches(group: HubToolGroup, entry: HubToolEntry, query: String): Boolean {
        val haystack = (group.title + " " + entry.title + " " + entry.description).lowercase(Locale.ROOT)
        return query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
            .all { it in haystack }
    }
}
