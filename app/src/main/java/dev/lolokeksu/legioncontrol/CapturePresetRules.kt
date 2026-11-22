package dev.lolokeksu.legioncontrol

/** Deliberately pure validation shared by Settings repository and Kotlin/JVM tests. */
object CapturePresetRules {
    private val supported = mapOf(
        "edge" to setOf(1280, 1920),
        "fps" to setOf(30, 60),
        "mbit" to setOf(4, 8, 12),
        "minutes" to setOf(1, 5, 15)
    )

    fun accepts(name: String, value: Int): Boolean = supported[name]?.contains(value) == true
    fun requireSupported(name: String, value: Int) {
        require(accepts(name, value)) { "Unsupported capture option" }
    }
}
