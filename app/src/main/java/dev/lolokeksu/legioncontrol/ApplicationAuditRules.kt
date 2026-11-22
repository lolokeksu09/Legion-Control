package dev.lolokeksu.legioncontrol

/** A package may be invisible to the caller even when installed. Never infer uninstall. */
enum class AppObservationEvent {
    FIRST_OBSERVED, VERSION_CHANGED, BECAME_VISIBLE, BECAME_UNAVAILABLE
}

data class AppFingerprint(
    val visible: Boolean,
    val versionCode: Long?,
    val updatedAtMs: Long?
)

data class AppObservation(
    val event: AppObservationEvent?,
    val description: String,
    val current: AppFingerprint
)

/** Side-effect-free model for unit tests and repository observation changes. */
object ApplicationAuditRules {
    fun classify(previous: AppFingerprint?, current: AppFingerprint): AppObservation {
        val event=when {
            previous==null && current.visible -> AppObservationEvent.FIRST_OBSERVED
            previous==null -> null
            previous.visible && !current.visible -> AppObservationEvent.BECAME_UNAVAILABLE
            !previous.visible && current.visible -> AppObservationEvent.BECAME_VISIBLE
            previous.visible && current.visible && (
                previous.versionCode!=current.versionCode ||
                    previous.updatedAtMs!=current.updatedAtMs) ->
                AppObservationEvent.VERSION_CHANGED
            else -> null
        }
        val message=when(event) {
            AppObservationEvent.FIRST_OBSERVED -> "Сведения о видимом приложении сохранены впервые."
            AppObservationEvent.VERSION_CHANGED -> "Изменилась версия или дата обновления пакета."
            AppObservationEvent.BECAME_VISIBLE -> "Пакет снова виден через Android PackageManager."
            AppObservationEvent.BECAME_UNAVAILABLE -> "Сведения о пакете недоступны; это НЕ доказательство удаления."
            null -> "Изменений в наблюдаемых данных не обнаружено."
        }
        return AppObservation(event,message,current)
    }

    fun versionLabel(versionName:String?, versionCode:Long?):String {
        val name=versionName?.takeIf { it.isNotBlank() } ?: "неизвестна"
        return if(versionCode==null) name else "$name (код $versionCode)"
    }

    fun humanDuration(millis:Long?):String {
        if(millis==null || millis<0) return "н/д"
        val minutes=millis/60000
        return "${minutes/60} ч ${minutes%60} мин"
    }

    fun csvCell(value:String):String {
        val trimmed=value.trimStart()
        val safe=if(trimmed.firstOrNull() in listOf('=','+','-','@')) "'"+value else value
        return "\"" + safe.replace("\"","\"\"") + "\""
    }
}
