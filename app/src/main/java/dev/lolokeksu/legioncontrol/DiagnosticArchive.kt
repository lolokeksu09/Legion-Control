package dev.lolokeksu.legioncontrol

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Streaming export, never a cloud sync or a collection service. */
data class DiagnosticArchiveCounts(
    val battery: Int,
    val attempts: Int,
    val sensors: Int,
    val thermal: Int,
    val timed: Int = 0
)

/**
 * CSV files are streamed into one ZIP. The source callbacks are invoked exactly
 * once, in order; bounded memory, no temporary plaintext copies on disk.
 */
object DiagnosticArchive {
    fun write(
        output: OutputStream,
        batteryCsv: (Writer) -> Int,
        attemptsCsv: (Writer) -> Int,
        sensorsCsv: (Writer) -> Int,
        thermalCsv: (Writer) -> Int,
        timedCsv: ((Writer) -> Int)? = null
    ): DiagnosticArchiveCounts = ZipOutputStream(output).use { zip ->
        val battery = entry(zip, "battery.csv", batteryCsv)
        val attempts = entry(zip, "sensor-attempts.csv", attemptsCsv)
        val sensors = entry(zip, "sensor-values.csv", sensorsCsv)
        val thermal = entry(zip, "thermal-history.csv", thermalCsv)
        val timed = timedCsv?.let { entry(zip, "timed-tests.csv", it) } ?: 0
        zip.putNextEntry(ZipEntry("README.txt"))
        zip.write(
            ("Legion Control diagnostic export\n" +
                "All files originate from local Android SQLite snapshots.\n" +
                "battery.csv preserves the existing 20-column export.\n" +
                "sensor-attempts.csv includes successful and failed sensor probes.\n" +
                "sensor-values.csv includes only accepted real SensorEvent values.\n" +
                "thermal-history.csv contains previously recorded manual measurements.\n" +
                (if (timedCsv != null) "timed-tests.csv contains user-started foreground battery tests.\n" else "") +
                "Missing values are blank, never synthetic zeros.\n" +
                "Do not treat display refresh rate as game FPS or thermal headroom as CPU temperature.\n" +
                "Rows: battery=$battery, sensor-attempts=$attempts, " +
                "sensor-values=$sensors, thermal-history=$thermal, timed-tests=$timed\n"
            ).toByteArray(Charsets.UTF_8)
        )
        zip.closeEntry()
        DiagnosticArchiveCounts(battery, attempts, sensors, thermal, timed)
    }

    private fun entry(
        zip: ZipOutputStream,
        name: String,
        block: (Writer) -> Int
    ): Int {
        zip.putNextEntry(ZipEntry(name))
        // Do not close this wrapper: that would close the underlying ZIP.
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        val rows = block(writer)
        writer.flush()
        zip.closeEntry()
        return rows
    }
}

/** RFC4180 quoting, Unicode BOM and formula-injection protection for OEM text. */
object DiagnosticArchiveCsv {
    fun begin(writer: Writer, columns: List<String>) {
        writer.write("\uFEFF")
        append(writer, columns)
    }

    fun append(writer: Writer, cells: List<String>, untrustedText: Set<Int> = emptySet()) {
        writer.write(cells.mapIndexed { i, raw ->
            CsvEscaper.cell(raw, untrusted = i in untrustedText)
        }.joinToString(","))
        writer.write("\r\n")
    }
}
