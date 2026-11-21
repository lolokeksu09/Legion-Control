package dev.lolokeksu.legioncontrol

import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedCsvReportTest {
    private fun reading(
        session: String = "first",
        elapsed: Long = 200_000L
    ) = StoredReading(
        sessionId = session,
        recordedAtMs = 1_800_000_000_000L,
        elapsedMs = elapsed,
        percent = 69,
        temperatureDeciC = 370,
        chargingStatus = "Заряжается",
        chargingSource = "Сеть, USB \"C\"",
        voltageMv = 4192,
        currentUa = 3_260_000,
        powerSaving = false,
        refreshRateHz = 120f,
        screenWidthPx = 1440,
        screenHeightPx = 3168
    )

    private fun diagnostic(
        session: String = "first",
        elapsed: Long = 200_000L
    ) = DiagnosticStoredSample(
        sessionId = session,
        elapsedMs = elapsed,
        recordedAtMs = 1_800_000_000_100L,
        batteryPercent = 69,
        batteryTemperatureDeciC = 370,
        thermalStatus = 2,
        thermalHeadroom = 0.92f,
        chargeCounterMicroAh = 5_531_816,
        currentNowMicroAmps = 3_260_000,
        currentAverageMicroAmps = 3_283_000,
        ramAvailableBytes = 6_000_000_000L
    )

    @Test fun extendedCsvRetainsLegacyColumnsAndExportsActualDiagnostics() {
        val out = StringWriter()
        CsvReport.beginExtended(out)
        CsvReport.appendExtended(out, reading(), diagnostic())
        val rows = out.toString().removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(2, rows.size)
        val columns = rows[0].split(",")
        assertEquals(20, columns.size)
        assertEquals("\"session_id\"", columns.first())
        assertEquals("\"display_height_px\"", columns[12])
        assertEquals("\"thermal_status\"", columns[13])
        assertEquals("\"diagnostic_timestamp_utc\"", columns.last())
        assertTrue(rows[1].contains("\"2\",\"0.92\",\"5531816\",\"3260000\",\"3283000\",\"6000000000\""))
        assertTrue(rows[1].contains("\"Сеть, USB \"\"C\"\"\""))
        assertTrue(rows[1].contains("\"2027-01-15T08:00:00.100Z\"").not()) // Exact timestamp is derived from source data, not guessed.
        assertTrue(rows[1].endsWith("\"2027-01-15T08:00:00.100Z\"").not())
        assertTrue(out.toString().startsWith("\uFEFF"))
    }

    @Test fun oldRowsHaveEmptyDiagnosticColumnsWithoutFabricatedZero() {
        val out = StringWriter()
        CsvReport.beginExtended(out)
        CsvReport.appendExtended(out, reading(), null)
        val rows = out.toString().split("\r\n").filter { it.isNotEmpty() }
        val cells = rows[1].split(",")
        // The legacy charging-source field contains a comma; use end-of-row to check nulls.
        assertTrue(rows[1].endsWith(Array(7) { "\"\"" }.joinToString(",")))
        assertFalse(rows[1].contains("\"null\""))
    }

    @Test fun mismatchedDiagnosticCannotBeAttachedToWrongLegacyRow() {
        val out = StringWriter()
        assertThrows(IllegalArgumentException::class.java) {
            CsvReport.appendExtended(out, reading(), diagnostic(session = "another"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CsvReport.appendExtended(out, reading(), diagnostic(elapsed = 200_001))
        }
        assertEquals("", out.toString())
    }
}
