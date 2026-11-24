package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Stable identifiers for real visible controls; no executable action is stored here. */
enum class FeatureModule(val displayName: String) {
    CAPTURE("Capture Studio"), AUDIO("Audio Lab"), HAPTIC("Haptic Lab"),
    NETWORK("Connectivity Observatory"), PROFILE("Smart Profiles"),
    APPS("Application Center"), SESSION("Game Session"), TOOLBOX("Game Toolbox")
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
        FeatureEntry("toolbox.undo", FeatureModule.TOOLBOX, "Возврат системных настроек", "Восстановить прежние настройки", "game toolbox undo rollback яркость")
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
