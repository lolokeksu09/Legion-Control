package dev.lolokeksu.legioncontrol

import android.app.Application
import android.util.Log

/** Recovery runs before any Activity or foreground Service reads local preferences. */
class LegionApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { LegionDataBackup.recoverPendingRestore(this) }
            .onFailure {
                // Keep the durable journal for a later retry. Do not log user data.
                Log.e("LegionRestore", "Pending restore failed: " + it.javaClass.simpleName)
            }
    }
}
