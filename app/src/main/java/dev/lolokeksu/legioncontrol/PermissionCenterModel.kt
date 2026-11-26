package dev.lolokeksu.legioncontrol

internal enum class CapabilityAccess(val label: String) {
    NOTIFICATIONS("Уведомления"),
    OVERLAY("Поверх окон"),
    USAGE("Статистика использования"),
    DND("Доступ к управлению DND"),
    WRITE_SETTINGS("Изменение системных настроек"),
    MEDIA_VOLUME("Управление громкостью"),
    PLAYBACK_AUDIO("Разрешение захвата игрового звука"),
    GAME_FPS("Измерение FPS чужой игры"),
    OEM_BOOST("Управление Touch Boost / Bypass / CPU-GPU")
}

internal data class CapabilityAccessSnapshot(private val states: Map<CapabilityAccess, AccessState>) {
    operator fun get(capability: CapabilityAccess): AccessState = states[capability] ?: AccessState.UNKNOWN
    fun report(): String = CapabilityAccess.entries.joinToString("\n") { "${it.label}: ${get(it).label}" }
}

/** Presentation policy is independent of Android services, Activities and Views. */
internal data class PermissionItem(
    val capability: CapabilityAccess,
    val title: String,
    val purpose: String,
    val state: AccessState,
    val canOpenSettings: Boolean
)

internal object PermissionCenterModel {
    private data class Definition(val capability: CapabilityAccess, val title: String,
                                  val purpose: String, val settings: Boolean)

    private val permissions = listOf(
        Definition(CapabilityAccess.OVERLAY, "Поверх других окон", "Плавающая панель Game Toolbox.", true),
        Definition(CapabilityAccess.USAGE, "Использование приложений", "Наблюдаемые события запуска и выхода из игры.", true),
        Definition(CapabilityAccess.NOTIFICATIONS, "Уведомления", "Управление активными сессиями и предупреждения.", true),
        Definition(CapabilityAccess.DND, "Режим «Не беспокоить»", "Право управления DND, не состояние самого режима.", true),
        Definition(CapabilityAccess.WRITE_SETTINGS, "Изменение системных настроек", "Яркость и тайм-аут экрана.", true),
        Definition(CapabilityAccess.PLAYBACK_AUDIO, "Захват игрового звука", "RECORD_AUDIO; без микрофона. Игра может запретить захват.", true)
    )
    private val limitations = listOf(
        Definition(CapabilityAccess.MEDIA_VOLUME, "Громкость мультимедиа", "Зависит от политики аудиоустройства.", false),
        Definition(CapabilityAccess.GAME_FPS, "FPS другой игры", "Частота дисплея и запрос FPS записи не являются FPS игры.", false),
        Definition(CapabilityAccess.OEM_BOOST, "Touch Boost / Bypass / CPU-GPU", "Нет поддерживаемого публичного API для прямого управления.", false)
    )

    private fun rows(definitions: List<Definition>, snapshot: CapabilityAccessSnapshot) =
        definitions.map { PermissionItem(it.capability, it.title, it.purpose,
            snapshot[it.capability], it.settings && snapshot[it.capability] != AccessState.UNSUPPORTED) }

    fun permissions(snapshot: CapabilityAccessSnapshot): List<PermissionItem> = rows(permissions, snapshot)
    fun limitations(snapshot: CapabilityAccessSnapshot): List<PermissionItem> = rows(limitations, snapshot)

    fun summary(items: List<PermissionItem>): String =
        "Доступно: " + items.count { it.state == AccessState.READY } + " из " + items.size +
            " · требуют внимания: " + items.count { it.state != AccessState.READY }
}
