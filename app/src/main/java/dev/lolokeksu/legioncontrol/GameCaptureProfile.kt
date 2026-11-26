package dev.lolokeksu.legioncontrol

/** Immutable, validated recording settings. Audio is always explicit and opt-in. */
data class GameCaptureProfile(
    val edge: Int,
    val fps: Int,
    val mbit: Int,
    val minutes: Int,
    val playbackAudio: Boolean
) {
    init {
        CapturePresetRules.requireSupported("edge", edge)
        CapturePresetRules.requireSupported("fps", fps)
        CapturePresetRules.requireSupported("mbit", mbit)
        CapturePresetRules.requireSupported("minutes", minutes)
    }

    fun setOption(option: String, value: Int): GameCaptureProfile {
        CapturePresetRules.requireSupported(option, value)
        return when (option) {
            "edge" -> copy(edge = value)
            "fps" -> copy(fps = value)
            "mbit" -> copy(mbit = value)
            "minutes" -> copy(minutes = value)
            else -> error("Unsupported capture option")
        }
    }

    fun withPlaybackAudio(enabled: Boolean): GameCaptureProfile = copy(playbackAudio = enabled)

    fun toConfig(): CaptureConfig = CaptureConfig.fromOptions(edge, fps, mbit, minutes)
}

/** Captured once per MediaProjection system consent; never silently switch game mid-request. */
data class ActiveGameCaptureSettings(
    val selectedGame: String?,
    val profile: GameCaptureProfile,
    val usingGameProfile: Boolean
)

object GameCaptureProfilePolicy {
    const val MAX_PROFILES = 64
    const val MAX_JSON_BYTES = 128 * 1024
    const val SCHEMA = 1

    private val packageRegex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    fun validPackage(name: String): Boolean =
        name.length in 3..160 && packageRegex.matches(name)

    /** An explicit package never falls back to a different game if it is unavailable. */
    fun selectGame(
        requestedGame: String?,
        toolboxGame: () -> String?,
        isLaunchable: (String) -> Boolean
    ): String? = (requestedGame ?: toolboxGame())?.takeIf { validPackage(it) && isLaunchable(it) }

    fun default(): GameCaptureProfile = GameCaptureProfile(1280, 30, 4, 5, false)
}
