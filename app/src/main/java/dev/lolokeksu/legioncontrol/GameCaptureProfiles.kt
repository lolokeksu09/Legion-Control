package dev.lolokeksu.legioncontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Isolated opt-in per-game recording overrides.
 * Uses its own SharedPreferences file: imports never clear legacy/global capture settings.
 * No installed-app scan, network transport, extra permission or auto-launch.
 */
class GameCaptureProfiles(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "legion-game-capture-profiles", Context.MODE_PRIVATE
    )

    private fun key(pkg: String): String {
        require(GameCaptureProfilePolicy.validPackage(pkg)) { "Invalid game package" }
        return "game.$pkg"
    }

    fun get(pkg: String): GameCaptureProfile? =
        runCatching { prefs.getString(key(pkg), null)?.let { decode(JSONObject(it)) } }
            .getOrNull()

    fun save(pkg: String, profile: GameCaptureProfile) {
        val target = key(pkg)
        if (!prefs.contains(target)) {
            check(prefs.all.keys.count { it.startsWith("game.") } <
                GameCaptureProfilePolicy.MAX_PROFILES) { "Profile storage is full" }
        }
        check(prefs.edit().putString(target, encode(profile).toString()).commit()) {
            "Game capture profile could not be saved"
        }
    }

    fun delete(pkg: String): Boolean {
        val target = key(pkg)
        if (!prefs.contains(target)) return false
        check(prefs.edit().remove(target).commit()) { "Game capture profile could not be deleted" }
        return true
    }

    fun count(): Int = prefs.all.keys.count { it.startsWith("game.") }

    /** Portable manual JSON, with no keys, tokens, recordings or system settings. */
    fun backupJson(): String {
        val array = JSONArray()
        for ((pkg, profile) in allProfiles().toSortedMap()) {
            array.put(encode(profile).put("package", pkg))
        }
        return JSONObject().put("schema", GameCaptureProfilePolicy.SCHEMA)
            .put("profiles", array).toString(2)
    }

    /**
     * Validate full input before atomic replacement. Invalid/duplicate package,
     * codec setting, schema or size never causes a partial import.
     */
    fun restoreJson(text: String): Int {
        require(text.toByteArray(Charsets.UTF_8).size <= GameCaptureProfilePolicy.MAX_JSON_BYTES) {
            "Backup is too large"
        }
        val root = JSONObject(text)
        require(root.opt("schema") == GameCaptureProfilePolicy.SCHEMA)
        require(root.keys().asSequence().toSet() == setOf("schema", "profiles")) { "Unsupported backup schema" }
        val array = root.opt("profiles") as? JSONArray
            ?: throw IllegalArgumentException("Missing profiles array")
        require(array.length() <= GameCaptureProfilePolicy.MAX_PROFILES)
        val parsed = linkedMapOf<String, GameCaptureProfile>()
        for (i in 0 until array.length()) {
            val obj = array.opt(i) as? JSONObject
                ?: throw IllegalArgumentException("Invalid profile item")
            val pkg = obj.opt("package") as? String
                ?: throw IllegalArgumentException("Missing package")
            require(GameCaptureProfilePolicy.validPackage(pkg))
            require(obj.keys().asSequence().toSet() == setOf("package", "edge", "fps", "mbit", "minutes", "audio"))
            require(!parsed.containsKey(pkg)) { "Duplicated package" }
            parsed[pkg] = decode(obj)
        }
        val editor = prefs.edit().clear()
        parsed.forEach { (pkg, profile) ->
            editor.putString(key(pkg), encode(profile).toString())
        }
        check(editor.commit()) { "Backup could not be restored" }
        return parsed.size
    }

    private fun allProfiles(): Map<String, GameCaptureProfile> =
        prefs.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith("game.")) return@mapNotNull null
            val pkg = key.removePrefix("game.")
            if (!GameCaptureProfilePolicy.validPackage(pkg)) return@mapNotNull null
            val profile = runCatching { decode(JSONObject(value as String)) }.getOrNull()
            if (profile == null) null else pkg to profile
        }.toMap()

    private fun encode(profile: GameCaptureProfile) = JSONObject()
        .put("edge", profile.edge)
        .put("fps", profile.fps)
        .put("mbit", profile.mbit)
        .put("minutes", profile.minutes)
        .put("audio", profile.playbackAudio)

    private fun decode(obj: JSONObject): GameCaptureProfile {
        fun int(key: String): Int = obj.opt(key) as? Int
            ?: throw IllegalArgumentException("Invalid integer: $key")
        val audio = obj.opt("audio") as? Boolean
            ?: throw IllegalArgumentException("Invalid audio option")
        return GameCaptureProfile(int("edge"), int("fps"), int("mbit"), int("minutes"), audio)
    }
}
