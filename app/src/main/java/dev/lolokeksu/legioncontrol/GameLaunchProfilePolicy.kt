package dev.lolokeksu.legioncontrol

/** User-defined values. Null means: never touch that global Android setting. */
data class GameLaunchProfile(
    val mediaPercent: Int? = null,
    val brightnessPercent: Int? = null,
    val focusEnabled: Boolean? = null
) {
    init {
        require(mediaPercent == null || mediaPercent in 0..100)
        require(brightnessPercent == null || brightnessPercent in 10..100)
    }

    val actionable: Boolean
        get() = mediaPercent != null || brightnessPercent != null || focusEnabled != null

    fun encode(): String = listOf(
        "v1",
        mediaPercent?.toString() ?: "-",
        brightnessPercent?.toString() ?: "-",
        when (focusEnabled) { true -> "on"; false -> "off"; null -> "-" }
    ).joinToString("|")

    companion object {
        fun decode(encoded: String?): GameLaunchProfile? {
            if (encoded == null) return null
            val fields = encoded.split('|')
            if (fields.size != 4 || fields[0] != "v1") return null
            return runCatching {
                fun parsePercent(field: String): Int? =
                    if (field == "-") null else field.toInt()
                val focus = when (fields[3]) {
                    "on" -> true
                    "off" -> false
                    "-" -> null
                    else -> error("Invalid focus value")
                }
                GameLaunchProfile(parsePercent(fields[1]), parsePercent(fields[2]), focus)
            }.getOrNull()
        }
    }
}

/** Original and requested Android values, not percentages. */
data class LaunchSettingChange(val original: Int, val requested: Int) {
    fun encode(): String = "$original,$requested"

    companion object {
        fun decode(encoded: String): LaunchSettingChange? {
            val pair = encoded.split(',')
            if (pair.size != 2) return null
            val before = pair[0].toIntOrNull() ?: return null
            val after = pair[1].toIntOrNull() ?: return null
            if (before < 0 || after < 0) return null
            return LaunchSettingChange(before, after)
        }
    }
}

enum class FocusUndoScope { GLOBAL_FILTER, APP_RULE }

/** The explicit undo record survives process death; no background restoration. */
data class GameLaunchUndo(
    val packageName: String,
    val media: LaunchSettingChange?,
    val brightness: LaunchSettingChange?,
    val brightnessMode: LaunchSettingChange?,
    val focus: LaunchSettingChange?,
    val focusScope: FocusUndoScope = FocusUndoScope.GLOBAL_FILTER,
    val focusRuleExisted: Boolean = true
) {
    val pending: Boolean
        get() = media != null || brightness != null || brightnessMode != null || focus != null

    /** v2 records the DND scope; v1 remains readable as a legacy global-filter ticket. */
    fun encode(): String = listOf(
        "v2", packageName, media?.encode() ?: "-", brightness?.encode() ?: "-",
        brightnessMode?.encode() ?: "-", focus?.encode() ?: "-",
        when (focusScope) { FocusUndoScope.GLOBAL_FILTER -> "global"; FocusUndoScope.APP_RULE -> "app_rule" },
        if (focusRuleExisted) "1" else "0"
    ).joinToString("|")

    companion object {
        fun decode(encoded: String?): GameLaunchUndo? {
            if (encoded == null) return null
            val fields = encoded.split('|')
            if (fields.size < 2 || !GameLaunchProfilePolicy.validPackage(fields[1])) return null
            fun parseChange(field: String): LaunchSettingChange? =
                if (field == "-") null else LaunchSettingChange.decode(field)
                    ?: error("Invalid undo entry")
            return runCatching {
                when (fields[0]) {
                    "v1" -> {
                        require(fields.size == 6)
                        GameLaunchUndo(fields[1], parseChange(fields[2]), parseChange(fields[3]),
                            parseChange(fields[4]), parseChange(fields[5]), FocusUndoScope.GLOBAL_FILTER, true)
                    }
                    "v2" -> {
                        require(fields.size == 8)
                        val scope = when (fields[6]) {
                            "global" -> FocusUndoScope.GLOBAL_FILTER
                            "app_rule" -> FocusUndoScope.APP_RULE
                            else -> error("Invalid focus scope")
                        }
                        val existed = when (fields[7]) { "1" -> true; "0" -> false; else -> error("Invalid focus rule state") }
                        GameLaunchUndo(fields[1], parseChange(fields[2]), parseChange(fields[3]),
                            parseChange(fields[4]), parseChange(fields[5]), scope, existed)
                    }
                    else -> null
                }
            }.getOrNull()
        }
    }
}
enum class RestoreChoice { RESTORE, ALREADY_ORIGINAL, CHANGED_BY_USER }

object GameLaunchProfilePolicy {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun validPackage(pkg: String): Boolean = pkg.length in 3..200 && PACKAGE.matches(pkg)

    fun mediaIndex(percent: Int, max: Int): Int {
        require(percent in 0..100 && max >= 0)
        return ((percent.toLong() * max + 50) / 100).toInt()
    }

    /** A platform setter returning normally is not proof of the restored value. */
    internal fun restoreAndVerify(read:()->Int,write:(Int)->Unit,change:LaunchSettingChange) {
        write(change.original)
        check(read()==change.original) { "Android did not confirm restored setting" }
    }

    /** Do not undo a setting that the user or another app changed meanwhile. */
    fun restoreChoice(current: Int, change: LaunchSettingChange): RestoreChoice = when (current) {
        change.original -> RestoreChoice.ALREADY_ORIGINAL
        change.requested -> RestoreChoice.RESTORE
        else -> RestoreChoice.CHANGED_BY_USER
    }
}

/** Encoded state of Legion's implicit DND rule; ruleId is runtime-only. */
internal data class AppDndRuleSnapshot(
    val exists: Boolean,
    val state: Int,
    val filter: Int,
    val ruleId: String? = null
) {
    val encoded: Int
        get() {
            if (!exists) return 0
            require(state in 0..1 && filter in 1..4)
            return 100 + state * 10 + filter
        }

    companion object {
        fun decode(encoded: Int): AppDndRuleSnapshot {
            if (encoded == 0) return AppDndRuleSnapshot(false, 0, 1)
            require(encoded in 101..114) { "Invalid app DND snapshot" }
            val state = (encoded / 10) % 10
            val filter = encoded % 10
            require(state in 0..1 && filter in 1..4) { "Invalid app DND snapshot" }
            return AppDndRuleSnapshot(true, state, filter)
        }
    }
}
