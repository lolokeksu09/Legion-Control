package dev.lolokeksu.legioncontrol

/** Truthful, Android-free summary of a saved one-shot launch profile and its restore ticket. */
internal object GameProfilePresentation {
    fun report(profile: GameLaunchProfile?, access: AccessState?,
               pendingPackage: String?, selectedPackage: String): String = buildString {
        if (profile == null || !profile.actionable) {
            append("Профиль запуска не настроен. Его можно создать в Game Toolbox.")
        } else {
            append("Громкость мультимедиа: ").append(profile.mediaPercent?.let { "$it%" } ?: "не менять")
            append("\nЯркость: ").append(profile.brightnessPercent?.let { "$it%" } ?: "не менять")
            append("\n«Не беспокоить»: ").append(when (profile.focusEnabled) {
                true -> "включить"
                false -> "выключить"
                null -> "не менять"
            })
            append("\nПроверка необходимых доступов: ").append(access?.label ?: AccessState.UNKNOWN.label)
        }
        if (pendingPackage == selectedPackage) {
            append("\nДля этой игры есть незавершённый возврат системных настроек.")
        } else if (pendingPackage != null) {
            append("\nСначала восстановите настройки предыдущей игры: ").append(pendingPackage)
        }
        append("\nНастройки общесистемные; профиль не запускает игру автоматически.")
    }
}
