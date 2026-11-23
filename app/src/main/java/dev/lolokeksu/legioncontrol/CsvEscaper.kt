package dev.lolokeksu.legioncontrol

/** Shared RFC-4180 quote and spreadsheet formula-injection guard for user/OEM text. */
object CsvEscaper {
    private val formula = setOf('=', '+', '-', '@')
    private val invisible = setOf('\uFEFF', '\u200B', '\u2060', '\u0000')

    fun cell(value: String, untrusted: Boolean = true): String {
        val normalized = value.dropWhile { it.isWhitespace() || it in invisible }
        val safe = if (untrusted && normalized.firstOrNull() in formula) "'" + value else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
