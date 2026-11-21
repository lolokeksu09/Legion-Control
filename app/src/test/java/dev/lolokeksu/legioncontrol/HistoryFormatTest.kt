package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class HistoryFormatTest {
    @Test fun russianMeasurementForms() {
        val examples = mapOf(
            0 to "0 измерений",
            1 to "1 измерение",
            2 to "2 измерения",
            3 to "3 измерения",
            4 to "4 измерения",
            5 to "5 измерений",
            11 to "11 измерений",
            12 to "12 измерений",
            14 to "14 измерений",
            21 to "21 измерение",
            22 to "22 измерения",
            25 to "25 измерений",
            101 to "101 измерение",
            112 to "112 измерений"
        )
        for ((count, expected) in examples) {
            assertEquals(expected, RussianQuantity.measurements(count))
        }
    }

    @Test fun csvContainsExactValuesAndEscapesMultilineQuotes() {
        val writer = StringWriter()
        CsvReport.begin(writer)
        CsvReport.append(writer, StoredReading(
            sessionId = "session-1", recordedAtMs = 1000L, elapsedMs = 201L,
            percent = 30, temperatureDeciC = 280,
            chargingStatus = "Не заряжается, да",
            chargingSource = "Тест \"кабель\"\nстрока",
            voltageMv = 3521, currentUa = -633000,
            powerSaving = false,
            refreshRateHz = 120f,
            screenWidthPx = 1440, screenHeightPx = 3168
        ))
        val value = writer.toString()
        assertTrue(value.startsWith("\uFEFF\"session_id\",\"timestamp_utc\""))
        assertTrue(value.contains("\"1970-01-01T00:00:01Z\""))
        assertTrue(value.contains("\"Тест \"\"кабель\"\"\nстрока\""))
        assertTrue(value.contains("\"-633000\""))
        assertTrue(value.contains("\"120.0\""))
        assertTrue(value.endsWith("\r\n"))
        assertEquals(2, value.split("\r\n").size - 1)
    }

    @Test fun missingMeasurementsUseEmptyColumnsNotFabricatedZero() {
        val writer = StringWriter()
        CsvReport.begin(writer)
        CsvReport.append(writer, StoredReading(
            sessionId = "x", recordedAtMs = 1L, elapsedMs = 1L,
            percent = 1, temperatureDeciC = 200,
            chargingStatus = "Неизвестно", chargingSource = "Неизвестно",
            voltageMv = null, currentUa = null, powerSaving = null,
            refreshRateHz = null, screenWidthPx = null, screenHeightPx = null
        ))
        assertTrue(writer.toString().contains("\"Неизвестно\",\"Неизвестно\",\"\",\"\",\"\",\"\",\"\",\"\""))
    }
}
