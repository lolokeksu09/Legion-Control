package dev.lolokeksu.legioncontrol

/**
 * Cancellation is per-request; a stale queued worker cannot be revived when a
 * newer request starts. Synchronization makes the queued/running distinction exact.
 */
class ConnectivityRunTicket {
    private var begun=false
    private var cancelled=false

    @Synchronized fun start():Boolean {
        if(begun || cancelled) return false
        begun=true
        return true
    }
    /** True iff the task had not begun when cancellation was requested. */
    @Synchronized fun cancel():Boolean {
        cancelled=true
        return !begun
    }
    @Synchronized fun isCancelled():Boolean=cancelled
}
