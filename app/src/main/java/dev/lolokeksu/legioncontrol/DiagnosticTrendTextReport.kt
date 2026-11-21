package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Pure textual delta report. Unknown source values are never converted to zero. */
object DiagnosticTrendTextReport {
    fun format(trend: DiagnosticTrend?): String {
        if (trend == null) {
            return "ДИНАМИКА · для сравнения нужно второе ручное измерение в одном сеансе"
        }
        return buildString {
            append("ДИНАМИКА МЕЖДУ ИЗМЕРЕНИЯМИ · ")
                .append(String.format(Locale.US, "%.1f с", trend.intervalMs / 1000.0))
                .append('\n')
            append("Изменение Thermal Status: ")
                .append(trend.thermalStatusDelta?.let { signedInt(it) }
                    ?: "недоступна")
                .append(" (разница кодов Android)\n")
            append("Изменение Thermal Headroom: ")
                .append(trend.thermalHeadroomDelta?.let {
                    String.format(Locale.US, "%+.2f", it)
                } ?: "недоступна")
                .append(" (больше — ближе к тяжёлому троттлингу)\n")
            append("Изменение счётчика заряда: ")
                .append(trend.chargeCounterDeltaMicroAh?.let {
                    String.format(Locale.US, "%+.1f мА·ч", it / 1000.0)
                } ?: "недоступна")
                .append('\n')
            append("Изменение уровня батареи: ")
                .append(trend.percentageDelta?.let { signedInt(it) + " п.п." }
                    ?: "недоступна")
                .append('\n')
            append("Согласованность направления уровня и счётчика заряда: ")
                .append(when (trend.batteryAgreement) {
                    BatteryCounterAgreement.INSUFFICIENT ->
                        "недостаточно данных для проверки"
                    BatteryCounterAgreement.AGREE ->
                        "направления совпадают (это не подтверждает точность ёмкости)"
                    BatteryCounterAgreement.DISAGREE ->
                        "направления расходятся; повторите замер"
                }).append('\n')
            append("Сравнение не является диагностикой износа батареи и не оценивает её полную ёмкость.")
        }
    }

    private fun signedInt(value: Int): String =
        String.format(Locale.US, "%+d", value)
}
