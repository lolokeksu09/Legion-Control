package dev.lolokeksu.legioncontrol

import java.util.Locale

data class ComparedSession(
    val name: String,
    val snapshot: SessionAnalyticsSnapshot
)

object SessionComparison {
    fun format(sessions: List<ComparedSession>): String {
        if (sessions.size < 2) {
            return "SESSION COMPARISON · нужны два сеанса с сохранёнными замерами. " +
                "Открой Legion Control и сделай несколько ручных измерений в разных сеансах."
        }
        val a = sessions[0]
        val b = sessions[1]
        return buildString {
            append("SESSION COMPARISON · по реальным записям SQLite\n")
            for (item in listOf(a,b)) {
                append(item.name).append(": ")
                    .append(item.snapshot.samples).append(" точек")
                    .append(" · Δ заряд ")
                    .append(item.snapshot.batteryPercentDelta?.let {
                        String.format(Locale.US, "%+d п.п.",it)
                    } ?: "н/д")
                    .append(" · Δ батарея ")
                    .append(item.snapshot.batteryTemperatureDeltaDeciC?.let {
                        String.format(Locale.US, "%+.1f °C",it/10.0)
                    } ?: "н/д")
                    .append("\n")
            }
            if (a.snapshot.elapsedMs != null && b.snapshot.elapsedMs != null) {
                append("Длительности между точками: ")
                    .append(String.format(Locale.US,"%.1f / %.1f мин\n",
                        a.snapshot.elapsedMs/60000.0,
                        b.snapshot.elapsedMs/60000.0))
            }
            append("Разное число точек и условия делают результаты несопоставимыми. ")
            append("Не нормализовано по нагрузке, не бенчмарк FPS.")
        }
    }
}
