package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHealthReportTest {
    private fun snapshot(
        code: Int? = 2,
        cycles: Int? = 173,
        chargingCode: Int? = 3,
        technology: String? = "Li-ion",
        present: Boolean? = true,
        batteryLow: Boolean? = false
    ) = BatteryHealthSnapshot(
        healthCode = code, cycleCount = cycles,
        chargingStatusCode = chargingCode, technology = technology,
        present = present, systemBatteryLow = batteryLow
    )

    @Test fun androidHealthIsNotDegradationPercentage() {
        assertEquals("GOOD · Android не обнаружил проблему", BatteryHealthSanitizer.healthLabel(2))
        assertEquals("OVERHEAT · перегрев", BatteryHealthSanitizer.healthLabel(3))
        assertEquals("DEAD · критическое состояние", BatteryHealthSanitizer.healthLabel(4))
        assertEquals("OVER_VOLTAGE · повышенное напряжение", BatteryHealthSanitizer.healthLabel(5))
        assertEquals("UNSPECIFIED_FAILURE · аппаратная ошибка",
            BatteryHealthSanitizer.healthLabel(6))
        assertEquals("COLD · низкая температура", BatteryHealthSanitizer.healthLabel(7))
        assertEquals("нет данных", BatteryHealthSanitizer.healthLabel(null))
        assertNull(BatteryHealthSanitizer.health(1))
        assertNull(BatteryHealthSanitizer.health(8))
        val text = BatteryHealthReport.format(snapshot(), 4023, 3_260_000)
        assertTrue(text.contains("Health: GOOD"))
        assertTrue(text.contains("Циклы зарядки (сообщает Android): 173"))
        assertTrue(text.contains("Технология батареи: Li-ion"))
        assertTrue(text.contains("Системный признак низкого заряда: нет"))
        assertTrue(text.contains("+13.11 Вт"))
        assertTrue(text.contains("не процент износа"))
        assertTrue(text.contains("не подтверждает мощность адаптера или наличие Bypass"))
    }

    @Test fun missingValuesDoNotBecomeZeroOrFalse() {
        val text = BatteryHealthReport.format(
            snapshot(code = null, cycles = null, chargingCode = null,
                technology = null, present = null, batteryLow = null),
            null, null
        )
        assertTrue(text.contains("Health: нет данных"))
        assertTrue(text.contains("Циклы зарядки (сообщает Android): нет данных"))
        assertTrue(text.contains("Батарея обнаружена Android: нет данных"))
        assertTrue(text.contains("Системный признак низкого заряда: нет данных"))
        assertTrue(text.contains("Расчётная мощность на клеммах батареи: нет данных"))
        assertFalse(text.contains("Циклы зарядки (сообщает Android): 0"))
        assertFalse(text.contains("Батарея обнаружена Android: нет"))
    }

    @Test fun sanitizersRejectInvalidOemValues() {
        assertEquals(0, BatteryHealthSanitizer.cycleCount(0))
        assertNull(BatteryHealthSanitizer.cycleCount(-1))
        assertNull(BatteryHealthSanitizer.cycleCount(100_001))
        assertNull(BatteryHealthSanitizer.chargingStatus(0))
        assertNull(BatteryHealthSanitizer.chargingStatus(101))
        assertEquals("Li-ion", BatteryHealthSanitizer.technology("  Li-ion  "))
        assertNull(BatteryHealthSanitizer.technology("  "))
        assertNull(BatteryHealthSanitizer.technology("bad\nvalue"))
        assertNull(BatteryHealthSanitizer.technology("a".repeat(61)))
    }

    @Test fun terminalElectricalPowerIsEstimateNotChargerWattage() {
        assertEquals(14.84487, BatteryHealthSanitizer.batteryPowerWatts(4023, 3_690_000)!!,
            0.00001)
        assertEquals(-2.0, BatteryHealthSanitizer.batteryPowerWatts(4000, -500_000)!!,
            0.00001)
        assertEquals(0.0, BatteryHealthSanitizer.batteryPowerWatts(4000, 0)!!, 0.00001)
        assertNull(BatteryHealthSanitizer.batteryPowerWatts(null, 500_000))
        assertNull(BatteryHealthSanitizer.batteryPowerWatts(4000, Int.MIN_VALUE))
        assertNull(BatteryHealthSanitizer.batteryPowerWatts(0, 500_000))
        assertNull(BatteryHealthSanitizer.batteryPowerWatts(4000, 60_000_000))
    }
}
