package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTextReportTest {
    private val display = ReportedDisplayMode(2, 1440, 3168, 120f)

    @Test fun successfulReadingsExposeSourcesAndActualUnits() {
        val snapshot = DeviceDiagnosticSnapshot(
            capabilities = PlatformCapabilities(
                thermalStatus = 2,
                sustainedPerformanceSupported = false,
                activeDisplayMode = display,
                supportedDisplayModes = listOf(display)
            ),
            resources = ResourceTelemetrySnapshot(
                elapsedRealtimeMs = 123456L,
                systemMemory = ProbeResult.available(MemoryMetrics(
                    totalBytes = 16_000_000_000L,
                    availableBytes = 4_000_000_000L,
                    lowMemoryThresholdBytes = 1_000_000_000L,
                    lowMemory = false
                )),
                appFilesystem = ProbeResult.available(StorageMetrics(1000, 350)),
                onlineProcessorCount = ProbeResult.available(8),
                batteryChargeCounterMicroAh = ProbeResult.available(2_500_000),
                batteryCurrentNowMicroAmps = ProbeResult.available(-633_000),
                batteryCurrentAverageMicroAmps = ProbeResult.available(0),
                batteryEnergyCounterNanoWh = ProbeResult.available(12_000_000_000L),
                thermalHeadroom = ProbeResult.available(1.2f)
            )
        )
        val text = DiagnosticsTextReport.format(snapshot)
        assertTrue(text.contains("Тепловой статус Android: 2"))
        assertTrue(text.contains("120.0 Гц"))
        assertTrue(text.contains("Поддержка Sustained Performance Mode: нет"))
        assertTrue(text.contains("Активность Sustained Performance Mode в других приложениях:"))
        assertTrue(text.contains("не определяется публичным Android API"))
        assertTrue(text.contains("Процессоров, доступных приложению: 8"))
        assertTrue(text.contains("Ток батареи (мгновенный): -633.0 мА"))
        assertTrue(text.contains("Ток батареи (средний): +0.0 мА"))
        assertTrue(text.contains("Тепловой запас Android: 1.20"))
        assertTrue(text.contains("нельзя считать FPS"))
    }

    @Test fun unavailableAndErrorsAreNotRenderedAsZero() {
        val snapshot = DeviceDiagnosticSnapshot(
            capabilities = PlatformCapabilities(null, null, null, emptyList()),
            resources = ResourceTelemetrySnapshot(
                elapsedRealtimeMs = 500,
                systemMemory = ProbeResult.unavailable(),
                appFilesystem = ProbeResult.failure(),
                onlineProcessorCount = ProbeResult.rateLimited(),
                batteryChargeCounterMicroAh = ProbeResult.unavailable(),
                batteryCurrentNowMicroAmps = ProbeResult.failure(),
                batteryCurrentAverageMicroAmps = ProbeResult.unavailable(),
                batteryEnergyCounterNanoWh = ProbeResult.unavailable(),
                thermalHeadroom = ProbeResult.rateLimited()
            )
        )
        val text = DiagnosticsTextReport.format(snapshot)
        assertTrue(text.contains("Тепловой статус Android: недоступно"))
        assertTrue(text.contains("RAM системы: недоступно"))
        assertTrue(text.contains("Файловая система приложения: ошибка Android API"))
        assertTrue(text.contains("Процессоров, доступных приложению: запрос ограничен"))
        assertTrue(text.contains("Тепловой запас Android: запрос ограничен"))
        assertFalse(text.contains("Тепловой запас Android: 0"))
    }
}
