package dev.lolokeksu.legioncontrol

/** Human-readable Celsius UI; SQLite and SmartRule thresholds remain in deci-Celsius. */
object SmartThresholdPresentation {
    fun showTemperature(deciC: Int): String = "${deciC / 10}.${(deciC % 10).toString().padStart(1, '0')}"

    fun parseTemperature(text: String): Int? {
        val normalized = text.trim().replace(',', '.')
        val match = Regex("^(\\d{2})(?:\\.(\\d))?$").matchEntire(normalized) ?: return null
        val degrees = match.groupValues[1].toIntOrNull() ?: return null
        val tenth = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        return (degrees * 10 + tenth).takeIf { it in 300..550 }
    }

    fun parseChargePercent(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 5..80 }
}
