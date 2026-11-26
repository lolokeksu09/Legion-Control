package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.util.concurrent.Executors

/** Crash recovery is scheduled before the launcher, but never blocks Application.onCreate. */
class LegionApplication : Application() {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val recoveryExecutor = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "legion-startup-recovery")
    }

    lateinit var startupRecovery: StartupRecoveryCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        // File existence is cheap; do not open SQLite on every clean launch.
        val pending = runCatching { LegionDataBackup.hasPendingRestore(this) }.getOrDefault(true)
        startupRecovery = StartupRecoveryCoordinator(
            initialState = if (pending) StartupRecoveryState.Running else StartupRecoveryState.Ready,
            executor = recoveryExecutor,
            dispatch = { action -> mainHandler.post(action); Unit },
            recover = {
                runCatching { LegionDataBackup.recoverPendingRestore(this) }
                    .onFailure {
                        // Keep the durable journal for a later retry. Do not log user data.
                        Log.e("LegionRestore", "Pending restore failed: " + it.javaClass.simpleName)
                    }.getOrThrow()
                Unit
            }
        )
        startupRecovery.start()
    }
}
