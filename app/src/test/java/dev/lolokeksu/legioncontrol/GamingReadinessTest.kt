package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class GamingReadinessTest {
    private fun sample() = GamingReadinessInput(
        batteryPercent = 72,
        batteryTemperatureDeciC = 360,
        thermalStatus = 0,
        powerSaving = false,
        refreshRateHz = 120f,
        networkConnected = true,
        networkValidated = true,
        memoryLow = false
    )

    @Test fun normalSnapshotDoesNotInventFpsOrGameServerLatency() {
        val found = GamingReadinessRules.analyze(sample())
        assertEquals(7,found.size)
        assertTrue(found.all { it.severity == ReadinessSeverity.OK })
        val report = GamingReadinessRules.format(found)
        assertTrue(report.contains("не доказательство FPS"))
        assertTrue(report.contains("не измерялась"))
    }

    @Test fun severeHeatOverridesOtherNormalConditions() {
        val found = GamingReadinessRules.analyze(sample().copy(
            thermalStatus=3, batteryTemperatureDeciC=460
        ))
        assertEquals(ReadinessSeverity.CRITICAL,found.single { it.key == "thermal" }.severity)
        assertEquals(ReadinessSeverity.CRITICAL,found.single { it.key == "temperature" }.severity)
        assertTrue(GamingReadinessRules.format(found).contains("ОГРАНИЧЕНИЕ"))
    }

    @Test fun unknownValuesStayUnknownInsteadOfGreenPass() {
        val none = GamingReadinessInput(null,null,null,null,null,null,null,null)
        val found = GamingReadinessRules.analyze(none)
        assertEquals(7,found.size)
        assertTrue(found.all { it.severity == ReadinessSeverity.UNKNOWN })
        val malformed = GamingReadinessRules.analyze(sample().copy(refreshRateHz=Float.NaN))
        assertEquals(ReadinessSeverity.UNKNOWN,malformed.single { it.key == "display" }.severity)
    }

    @Test fun saverLowBatteryLowRateAndUnvalidatedNetworkAreWarnings() {
        val v = sample().copy(
            batteryPercent=12, batteryTemperatureDeciC=415,
            powerSaving=true,refreshRateHz=60f,
            networkConnected=true,networkValidated=false,memoryLow=true
        )
        val found = GamingReadinessRules.analyze(v)
        for (key in listOf("battery","temperature","power","display","network","memory")) {
            assertEquals(key,ReadinessSeverity.CAUTION,
                found.single { it.key == key }.severity)
        }
        assertEquals(ReadinessSeverity.OK,found.single { it.key == "thermal" }.severity)
    }

    @Test fun boundedComputeChecksumIsDeterministicAndCancelable() {
        val first = ComputeProbeMath.checksum(25)
        assertEquals(first,ComputeProbeMath.checksum(25))
        assertNotEquals(first,ComputeProbeMath.checksum(26))
        assertThrows(IllegalArgumentException::class.java) {
            ComputeProbeMath.checksum(ComputeProbeMath.ITERATIONS + 1)
        }
        assertThrows(InterruptedException::class.java) {
            ComputeProbeMath.checksum(100,stopRequested={ true })
        }
        val report = ComputeProbeMath.format(ComputeProbeResult(25,1000000L,first))
        assertTrue(report.contains("1.00 мс"))
        assertTrue(report.contains("Не частота CPU/GPU"))
    }
}
