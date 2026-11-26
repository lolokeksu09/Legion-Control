package dev.lolokeksu.legioncontrol

/** A successful setter is not proof of the value accepted by Android. */
internal object VerifiedSettingWrite {
    fun apply(requested: Int, read: () -> Int, write: (Int) -> Boolean): Int {
        check(write(requested)) { "Android rejected setting" }
        val actual = read()
        check(actual == requested) { "Android did not confirm setting" }
        return actual
    }
}
