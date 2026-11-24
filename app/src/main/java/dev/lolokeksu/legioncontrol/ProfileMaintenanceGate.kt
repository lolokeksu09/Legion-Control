package dev.lolokeksu.legioncontrol

import java.util.concurrent.locks.ReentrantLock

/**
 * Serializes ZIP restore/replay and global launch-profile writes. Non-blocking
 * acquisition avoids waiting on a long SQLite import from a UI callback.
 * Reentrancy supports rollback and recovery on the owning thread.
 */
internal object ProfileMaintenanceGate {
    private val lock=ReentrantLock()
    fun <T> tryRun(blocked:()->T,work:()->T):T {
        if(!lock.tryLock())return blocked()
        try { return work() } finally { lock.unlock() }
    }
}
