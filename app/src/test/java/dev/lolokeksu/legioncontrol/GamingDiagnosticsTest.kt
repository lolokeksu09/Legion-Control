package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class GamingDiagnosticsTest {
    @Test fun tcpJitterMeasuresVariationNotICMPPing() {
        val metrics = requireNotNull(TcpProbeStatistics.analyze(
            listOf(10.0, 20.0, 30.0, 40.0)
        ))
        assertEquals(10.0, metrics.minimumMs, 0.01)
        assertEquals(25.0, metrics.averageMs, 0.01)
        assertEquals(40.0, metrics.maximumMs, 0.01)
        assertEquals(10.0, metrics.jitterMs, 0.01)
        assertNull(TcpProbeStatistics.analyze(emptyList()))
        assertNull(TcpProbeStatistics.analyze(listOf(Double.NaN)))
    }

    @Test fun connectionFailuresAreDistinctFromLatency() {
        val result = TcpProbeResult(
            "one.one.one.one", 443, null, listOf(24.5), 4, 5, true
        )
        val report = TcpProbeStatistics.format(result)
        assertTrue(report.contains("1/5 успешных"))
        assertTrue(report.contains("DNS: не удалось"))
        assertTrue(report.contains("НЕ игровой ping"))
    }

    @Test fun framePacingNeverReportsWildRiftFps() {
        val report = FramePacingStatistics.format(
            FrameIntervalReport(listOf(8.3, 8.4, 17.0, 8.2), 120f)
        )
        assertTrue(report.contains("p95"))
        assertTrue(report.contains("Интервалов > 1.5× номинального: 1"))
        assertTrue(report.contains("Не FPS Wild Rift"))
        assertTrue(FramePacingStatistics.format(
            FrameIntervalReport(emptyList(), 120f)
        ).contains("недостаточно"))
    }

    @Test fun gameTimeIsEventBasedAndDoesNotInventMatches() {
        val report = GameUsageDiagnostics.format(GameUseResult(
            GameUsageDiagnostics.DEFAULT_PACKAGE, 3, 6*60_000L, true
        ))
        assertTrue(report.contains("6.0 мин"))
        assertTrue(report.contains("неполные"))
        assertTrue(report.contains("не игровая статистика матчей"))
    }

    @Test fun chargingComparisonOnlyReportsAvailableMeasurements() {
        val old = ChargeReading(
            1_000L, 70, 410, 4200, -1_000_000, "Разряжается"
        )
        val next = ChargeReading(
            11_000L, 69, 420, 4200, -700_000, "Разряжается"
        )
        val report = ChargingDiagnostics.format(next, old)
        assertTrue(report.contains("+1.0 °C"))
        assertTrue(report.contains("+300 мА"))
        assertTrue(report.contains("НЕ мощность адаптера"))
        val unavailable = ChargingDiagnostics.format(
            next.copy(currentMicroAmps = null, voltageMillivolts = null), null
        )
        assertTrue(unavailable.contains("н/д"))
        assertFalse(unavailable.contains("Произведение U×I"))
    }

    @Test fun previousSessionIsNecessaryForComparison() {
        val a = SessionAnalyticsSnapshot(
            "a", 3, 60_000L, -2, 20, 350, 370, 60f, 120f, 0, 0
        )
        val b = a.copy(sessionId = "b", batteryPercentDelta = -1)
        val format = SessionComparison.format(
            listOf(ComparedSession("Последний", a), ComparedSession("Предыдущий", b))
        )
        assertTrue(format.contains("-2 п.п."))
        assertTrue(format.contains("-1 п.п."))
        assertTrue(format.contains("не бенчмарк FPS"))
        assertTrue(SessionComparison.format(listOf(ComparedSession("Один", a)))
            .contains("нужны два сеанса"))
    }
}
