package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Formatting is shared by single probes and the combined manual overview. */
internal object DeviceOverviewPresentation {
    internal fun <T : Any> display(result: ProbeResult<T>, format: (T) -> String): String =
        when (result.state) {
            ProbeState.AVAILABLE -> format(requireNotNull(result.value))
            ProbeState.UNAVAILABLE -> "н/д · не предоставлено Android"
            ProbeState.ERROR -> "н/д · ошибка чтения"
            ProbeState.RATE_LIMITED -> "н/д · ограничена частота запросов"
        }

    private fun gib(bytes: Long) = String.format(Locale.US, "%.2f ГиБ", bytes / 1073741824.0)
    private fun current(value: Int) = String.format(Locale.US, "%+.0f мА", value / 1000.0)

    fun battery(charge: ProbeResult<Int>, energy: ProbeResult<Long>,
                now: ProbeResult<Int>, average: ProbeResult<Int>): String = buildString {
        append("БАТАРЕЯ · счётчики Android\n")
        append("Остаточный заряд: ").append(display(charge) {
            String.format(Locale.US, "%.1f мА·ч", it / 1000.0)
        }).append('\n')
        append("Счётчик энергии: ").append(display(energy) {
            String.format(Locale.US, "%.2f мВт·ч", it / 1_000_000.0)
        }).append('\n')
        append("Ток: ").append(display(now, ::current)).append('\n')
        append("Средний ток: ").append(display(average, ::current)).append('\n')
        append("Это показания батареи, не паспортная ёмкость, износ или мощность адаптера.")
    }

    fun thermal(status: Int?, headroom: ProbeResult<Float>): String = buildString {
        append("НАГРЕВ · Android Thermal Status\n")
        append("Статус: ").append(ThermalSeverity.describe(status)).append('\n')
        append("Thermal Headroom: ").append(display(headroom) { String.format(Locale.US, "%.2f", it) }).append('\n')
        if (status != null && status >= 3) append("Серьёзное тепловое ограничение; не запускайте нагрузочные тесты.\n")
        append("Headroom — системная оценка, не температура CPU/GPU и не процент.")
    }

    fun system(memory: ProbeResult<MemoryMetrics>, storage: ProbeResult<StorageMetrics>,
               pssMiB: Long?, hz: Float?, saving: Boolean?): String = buildString {
        append("СИСТЕМА · RAM, хранилище и дисплей\n")
        append("RAM доступно/всего: ").append(display(memory) {
            gib(it.availableBytes) + " / " + gib(it.totalBytes)
        }).append('\n')
        append("Android lowMemory: ").append(display(memory) { if (it.lowMemory) "да" else "нет" }).append('\n')
        append("Хранилище приложения доступно/всего: ").append(display(storage) {
            gib(it.availableBytes) + " / " + gib(it.totalBytes)
        }).append('\n')
        append("PSS Legion Control: ").append(pssMiB?.let { "$it МиБ" } ?: "н/д").append('\n')
        append("Частота дисплея: ").append(hz?.let { String.format(Locale.US, "%.1f Гц", it) } ?: "н/д").append('\n')
        append("Энергосбережение: ").append(when(saving) { true -> "включено"; false -> "выключено"; null -> "н/д" }).append('\n')
        append("Не измеряет FPS игры и память чужих процессов.")
    }
}
