package dev.lolokeksu.legioncontrol

import android.content.Context
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface StartupRecoveryState {
    data object Ready : StartupRecoveryState
    data object Running : StartupRecoveryState
    data class Failed(val reason: String) : StartupRecoveryState
    data class ContinuedWithWarning(val reason: String) : StartupRecoveryState
}

/** Crash recovery runs off main and exposes a small observable gate to the launcher. */
internal class StartupRecoveryCoordinator(
    initialState: StartupRecoveryState,
    private val executor: Executor,
    private val dispatch: ((() -> Unit) -> Unit),
    private val recover: () -> Unit
) {
    private val lock = Any()
    private var current = initialState
    private val started = AtomicBoolean(false)
    private val listeners = LinkedHashSet<(StartupRecoveryState) -> Unit>()

    val state: StartupRecoveryState
        get() = synchronized(lock) { current }

    fun observe(listener: (StartupRecoveryState) -> Unit): AutoCloseable {
        synchronized(lock) { listeners.add(listener) }
        dispatch {
            val latest = synchronized(lock) { if (listeners.contains(listener)) current else null }
            latest?.let(listener)
        }
        return AutoCloseable { synchronized(lock) { listeners.remove(listener) } }
    }

    /** A clean installation skips recovery; a pending journal is processed exactly once. */
    fun start() {
        if (state != StartupRecoveryState.Running || !started.compareAndSet(false, true)) return
        executor.execute {
            val result = runCatching { recover() }
            synchronized(lock) {
                if (current == StartupRecoveryState.Running) {
                    current = result.fold(
                        { StartupRecoveryState.Ready },
                        { StartupRecoveryState.Failed(it.javaClass.simpleName.ifBlank { "Unknown" }) }
                    )
                }
            }
            notifyListeners()
        }
    }

    /** User may enter after seeing failure; the durable journal is not discarded. */
    fun continueAfterFailure(): Boolean {
        val changed = synchronized(lock) {
            val failed = current as? StartupRecoveryState.Failed
            if (failed == null) false else {
                current = StartupRecoveryState.ContinuedWithWarning(failed.reason)
                true
            }
        }
        if (changed) notifyListeners()
        return changed
    }

    fun markRecovered() {
        val changed = synchronized(lock) {
            when (current) {
                is StartupRecoveryState.Failed, is StartupRecoveryState.ContinuedWithWarning -> {
                    current = StartupRecoveryState.Ready
                    true
                }
                else -> false
            }
        }
        if (changed) notifyListeners()
    }

    private fun notifyListeners() {
        val snapshot = synchronized(lock) { listeners.toList() }
        snapshot.forEach { listener ->
            dispatch {
                val latest = synchronized(lock) { if (listeners.contains(listener)) current else null }
                latest?.let(listener)
            }
        }
    }
}

internal object StartupRecoveryAccessPolicy {
    fun allowsUserStartedServices(state: StartupRecoveryState): Boolean =
        state == StartupRecoveryState.Ready || state is StartupRecoveryState.ContinuedWithWarning
}

/** Service starts are denied until recovery completes or the user acknowledges the warning. */
internal fun Context.startupRecoveryAllowsServiceStart(): Boolean {
    val app = applicationContext as? LegionApplication ?: return true
    return StartupRecoveryAccessPolicy.allowsUserStartedServices(app.startupRecovery.state)
}
