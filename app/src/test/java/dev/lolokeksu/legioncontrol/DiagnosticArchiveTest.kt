package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.zip.ZipInputStream

class DiagnosticArchiveTest {
    @Test fun zipIncludesAllDatasetsAndDoesNotLoseRows() {
        val out = ByteArrayOutputStream()
        val counts = DiagnosticArchive.write(
            out,
            batteryCsv = { writer ->
                writer.write("battery-header\r\nbattery-data\r\n")
                1
            },
            attemptsCsv = { writer ->
                DiagnosticArchiveCsv.begin(writer, listOf("state", "sensor_name"))
                DiagnosticArchiveCsv.append(
                    writer, listOf("TIMEOUT", "OEM light, sensor"), setOf(1)
                )
                1
            },
            sensorsCsv = { writer ->
                DiagnosticArchiveCsv.begin(writer, listOf("kind", "x"))
                DiagnosticArchiveCsv.append(writer, listOf("4", "-0.3"))
                1
            },
            thermalCsv = { writer ->
                DiagnosticArchiveCsv.begin(writer, listOf("thermal_status"))
                0
            }
        )
        assertEquals(DiagnosticArchiveCounts(1, 1, 1, 0), counts)
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                entries[item.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertEquals(
            listOf("battery.csv", "sensor-attempts.csv", "sensor-values.csv",
                "thermal-history.csv", "README.txt"),
            entries.keys.toList()
        )
        assertTrue(entries.getValue("battery.csv").contains("battery-data"))
        assertTrue(entries.getValue("sensor-attempts.csv").contains("\"OEM light, sensor\""))
        assertTrue(entries.getValue("README.txt").contains("battery=1"))
        assertTrue(entries.getValue("README.txt").contains("sensor-values=1"))
        assertTrue(entries.getValue("README.txt").contains("thermal-history=0"))
    }

    @Test fun injectionSafeCsvEscapesQuotesNewlinesAndOemFormula() {
        val writer = StringWriter()
        DiagnosticArchiveCsv.begin(writer, listOf("sensor_name", "state", "latency_ms"))
        DiagnosticArchiveCsv.append(writer, listOf("=SUM(1,2)", "CAPTURED", "50"), setOf(0))
        DiagnosticArchiveCsv.append(
            writer, listOf("  +oem \"name\"\nnext", "TIMEOUT", ""), setOf(0)
        )
        val result = writer.toString()
        assertTrue(result.startsWith("\uFEFF"))
        assertTrue(result.contains("\"'=SUM(1,2)\""))
        assertTrue(result.contains("\"'  +oem \"\"name\"\"\nnext\""))
        assertTrue(result.contains("\"TIMEOUT\",\"\""))
    }

    @Test fun sourceFailureFailsArchiveRatherThanFakingSuccess() {
        assertThrows(IllegalStateException::class.java) {
            DiagnosticArchive.write(
                ByteArrayOutputStream(),
                batteryCsv = { throw IllegalStateException("read failed") },
                attemptsCsv = { 0 }, sensorsCsv = { 0 }, thermalCsv = { 0 }
            )
        }
    }
}
