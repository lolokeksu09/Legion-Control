package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class HardwareLabMetricsTest {
    @Test fun storageThroughputRequiresPositiveRealDurationsAndBytes() {
        assertEquals(8.0,
            SandboxIoMetrics.mebibytesPerSecond(8L*1_048_576L,1_000_000_000L)!!,
            0.001
        )
        assertNull(SandboxIoMetrics.mebibytesPerSecond(0L,1_000_000_000L))
        assertNull(SandboxIoMetrics.mebibytesPerSecond(1024L,0L))
        assertTrue(SandboxIoMetrics.format(
            SandboxIoResult(8L*1_048_576L, 1_000_000_000L, 500_000_000L,true)
        ).contains("Контроль данных SHA-256: совпадает"))
        assertTrue(SandboxIoMetrics.format(
            SandboxIoResult(4L,1L,1L,false)
        ).contains("Контроль данных SHA-256: ОШИБКА"))
    }

    @Test fun motionReportComputesOnlyAvailableFiniteActualValues() {
        val accel = listOf(
            MotionObservation(0.0, 0.0, 9.8),
            MotionObservation(0.0, 0.0, 9.8),
            MotionObservation(Double.NaN, 0.0, 0.0)
        )
        val gyro = listOf(
            MotionObservation(0.0, 0.0, 0.0),
            MotionObservation(0.1, 0.0, 0.0)
        )
        val report = MotionMetrics.summarize(accel,gyro)
        assertEquals(2,report.accelerationCount)
        assertEquals(2,report.gyroCount)
        assertEquals(0.0,report.inclinationDeg!!, 0.001)
        assertEquals(0.0,report.accelerationMagnitudeVariation!!,0.001)
        assertEquals(kotlin.math.sqrt(0.005), report.gyroRmsRadPerSec!!,0.0001)
        assertTrue(MotionMetrics.format(report).contains("не является компасом"))
    }

    @Test fun motionMissingSensorsCannotBeRenderedAsValidAngles() {
        val report = MotionMetrics.summarize(
            emptyList(), listOf(MotionObservation(0.0,0.0,1.0))
        )
        assertNull(report.inclinationDeg)
        assertNull(report.accelerationMagnitudeVariation)
        assertEquals(1.0,report.gyroRmsRadPerSec!!,0.01)
        assertTrue(MotionMetrics.format(report).contains("недостаточно данных"))
        val sideways = MotionMetrics.summarize(
            listOf(MotionObservation(9.8,0.0,0.0)),emptyList()
        )
        assertEquals(90.0,sideways.inclinationDeg!!,0.001)
        assertNull(sideways.gyroRmsRadPerSec)
    }

    @Test fun networkTransitionHistoryIsBoundedForReporting() {
        val observations = (1..30).map {
            NetworkTransition("доступна активная сеть",it*1_000L)
        }
        val report = NetworkTransitionMetrics.format(observations,30_000L)
        assertTrue(report.contains("событий: 30"))
        assertTrue(report.contains("30.0 с"))
        assertFalse(report.contains("· 1000 мс:"))
        assertTrue(report.contains("30000 мс:"))
        assertTrue(NetworkTransitionMetrics.format(emptyList(),1000L)
            .contains("Изменений за окно не зарегистрировано"))
        assertTrue(report.contains("не ping игры"))
    }
}
