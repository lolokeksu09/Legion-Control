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

    private val diagnosticHeader = listOf(
        "thermal_status", "thermal_headroom", "charge_counter_micro_ah",
        "current_now_micro_a", "current_average_micro_a", "ram_available_bytes",
        "diagnostic_timestamp_utc"
    )

    /** v2 diagnostics are optional: old v1 rows produce seven blank cells. */
    fun beginExtended(writer: Writer) {
        writer.write("\uFEFF")
        row(writer, header + diagnosticHeader)
    }

    fun appendExtended(
        writer: Writer,
        reading: StoredReading,
        diagnostic: DiagnosticStoredSample?
    ) {
        require(diagnostic == null || (
            diagnostic.sessionId == reading.sessionId &&
                diagnostic.elapsedMs == reading.elapsedMs
        )) { "Diagnostic belongs to another measurement" }
        row(writer, legacyCells(reading) + listOf(
            diagnostic?.thermalStatus?.toString().orEmpty(),
            diagnostic?.thermalHeadroom?.takeIf { it.isFinite() }?.toString().orEmpty(),
            diagnostic?.chargeCounterMicroAh?.toString().orEmpty(),
            diagnostic?.currentNowMicroAmps?.toString().orEmpty(),
            diagnostic?.currentAverageMicroAmps?.toString().orEmpty(),
            diagnostic?.ramAvailableBytes?.toString().orEmpty(),
            diagnostic?.recordedAtMs?.let {
                Instant.ofEpochMilli(it).toString()
            }.orEmpty()
        ))
    }

    fun append(writer: Writer, reading: StoredReading) {
        row(writer, legacyCells(reading))
    }

    private fun legacyCells(reading: StoredReading): List<String> =
        listOf(
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
        )

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
