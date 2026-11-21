package dev.lolokeksu.legioncontrol

import java.io.Writer
import java.time.Instant

/** Streaming RFC4180-style CSV, exported only with explicit user consent via SAF. */
object CsvReport {
    private val header = listOf(
        "session_id", "timestamp_utc", "elapsed_ms", "battery_percent",
        "battery_temperature_deci_c", "charging_status", "charging_source",
        "voltage_mv", "current_ua", "power_saving", "display_refresh_hz",
        "display_width_px", "display_height_px"
    )

    fun begin(writer: Writer) {
        writer.write("\uFEFF")
        row(writer, header)
    }

    fun append(writer: Writer, reading: StoredReading) {
        row(writer, listOf(
            reading.sessionId,
            Instant.ofEpochMilli(reading.recordedAtMs).toString(),
            reading.elapsedMs.toString(),
            reading.percent.toString(),
            reading.temperatureDeciC.toString(),
            reading.chargingStatus,
            reading.chargingSource,
            reading.voltageMv?.toString().orEmpty(),
            reading.currentUa?.toString().orEmpty(),
            reading.powerSaving?.let { if (it) "true" else "false" }.orEmpty(),
            reading.refreshRateHz?.toString().orEmpty(),
            reading.screenWidthPx?.toString().orEmpty(),
            reading.screenHeightPx?.toString().orEmpty()
        ))
    }

    private fun row(writer: Writer, values: List<String>) {
        writer.write(values.joinToString(",") { value ->
            // Always quote to preserve commas, newlines and quotes from OEM labels.
            "\"" + value.replace("\"", "\"\"") + "\""
        })
        writer.write("\r\n")
    }
}

/** Russian counting forms; independent of Android resources for JVM regression tests. */
object RussianQuantity {
    fun measurements(count: Int): String {
        val lastTwo = count % 100
        val noun = when {
            lastTwo in 11..14 -> "измерений"
            count % 10 == 1 -> "измерение"
            count % 10 in 2..4 -> "измерения"
            else -> "измерений"
        }
        return "$count $noun"
    }
}
