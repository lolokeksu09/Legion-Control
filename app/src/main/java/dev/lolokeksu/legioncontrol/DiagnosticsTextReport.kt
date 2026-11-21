package dev.lolokeksu.legioncontrol

import java.util.Locale

/**
 * Pure text presentation of already-read public Android capabilities.
 * No Android UI, service calls, persistence, permissions or synthetic data.
 */
object DiagnosticsTextReport {
    fun format(snapshot: DeviceDiagnosticSnapshot): String = buildString {
        val capabilities = snapshot.capabilities
        val resources = snapshot.resources

        append("СИСТЕМНАЯ ДИАГНОСТИКА · публичный Android API\n")
        append("Тепловой статус Android: ")
            .append(ThermalSeverity.describe(capabilities.thermalStatus)).append('\n')
        append("Поддержка Sustained Performance Mode: ").append(
            when (capabilities.sustainedPerformanceSupported) {
                true -> "да"
                false -> "нет"
                null -> "неизвестно"
            }
        ).append('\n')

        append("Активный режим дисплея: ")
            .append(capabilities.activeDisplayMode?.let(::mode) ?: "недоступно").append('\n')
        append("Режимы дисплея (заявленные Android): ")
            .append(capabilities.supportedDisplayModes.takeIf { it.isNotEmpty() }
                ?.joinToString("; ", transform = ::mode) ?: "недоступно").append('\n')

        append("RAM системы: ").append(field(resources.systemMemory) {
            "доступно ${gib(it.availableBytes)} ГиБ из ${gib(it.totalBytes)} ГиБ; " +
                "порог ${gib(it.lowMemoryThresholdBytes)} ГиБ; lowMemory=${it.lowMemory}"
        }).append('\n')
        append("Файловая система приложения: ").append(field(resources.appFilesystem) {
            "доступно ${gib(it.availableBytes)} ГиБ из ${gib(it.totalBytes)} ГиБ"
        }).append('\n')
        append("Процессоров, доступных приложению: ")
            .append(field(resources.onlineProcessorCount) { it.toString() }).append('\n')

        append("Счётчик заряда батареи: ")
            .append(field(resources.batteryChargeCounterMicroAh) { "$it мкА·ч" }).append('\n')
        append("Ток батареи (мгновенный): ")
            .append(field(resources.batteryCurrentNowMicroAmps) { signedMilliAmps(it) }).append('\n')
        append("Ток батареи (средний): ")
            .append(field(resources.batteryCurrentAverageMicroAmps) { signedMilliAmps(it) }).append('\n')
        append("Счётчик энергии батареи: ")
            .append(field(resources.batteryEnergyCounterNanoWh) { "$it нВт·ч" }).append('\n')
        append("Тепловой запас Android: ")
            .append(field(resources.thermalHeadroom) {
                String.format(Locale.US, "%.2f", it)
            }).append('\n')
        append("Замер elapsedRealtime: ").append(resources.elapsedRealtimeMs)
            .append(" мс после запуска Android\n")
        append("Частоту дисплея нельзя считать FPS, а тепловой запас — температурой CPU/GPU.")
    }

    private fun <T : Any> field(probe: ProbeResult<T>, format: (T) -> String): String =
        when (probe.state) {
            ProbeState.AVAILABLE -> probe.value?.let(format) ?: "недоступно"
            ProbeState.UNAVAILABLE -> "недоступно"
            ProbeState.ERROR -> "ошибка Android API"
            ProbeState.RATE_LIMITED -> "запрос ограничен"
        }

    private fun mode(it: ReportedDisplayMode): String =
        "${it.widthPx}×${it.heightPx} · " +
            String.format(Locale.US, "%.1f Гц", it.refreshRateHz) +
            " (modeId=${it.modeId})"

    private fun gib(bytes: Long): String =
        String.format(Locale.US, "%.2f", bytes / (1024.0 * 1024 * 1024))

    private fun signedMilliAmps(microAmps: Int): String =
        String.format(Locale.US, "%+.1f мА", microAmps / 1000.0)
}
