package dev.lolokeksu.legioncontrol

/** One process-wide owner for MediaProjection (video, Replay or screenshots). */
enum class CaptureMode { RECORDING, REPLAY, SCREENSHOT }
enum class CapturePhase { IDLE, CONSENT_PENDING, STARTING, ACTIVE, STOPPING }

object CaptureCoordinator {
    private var owner: CaptureMode? = null
    private var phase = CapturePhase.IDLE

    @Synchronized fun reserveConsent(mode: CaptureMode): Boolean {
        if (owner != null) return false
        owner = mode
        phase = CapturePhase.CONSENT_PENDING
        return true
    }

    /** A service can only consume the fresh, single-use consent that its UI reserved. */
    @Synchronized fun begin(mode: CaptureMode): Boolean {
        if (owner != mode || phase != CapturePhase.CONSENT_PENDING) return false
        phase = CapturePhase.STARTING
        return true
    }

    @Synchronized fun markActive(mode: CaptureMode) {
        if (owner == mode && phase == CapturePhase.STARTING) phase = CapturePhase.ACTIVE
    }

    @Synchronized fun markStopping(mode: CaptureMode) {
        if (owner == mode && phase != CapturePhase.IDLE) phase = CapturePhase.STOPPING
    }

    @Synchronized fun release(mode: CaptureMode) {
        if (owner == mode) {
            owner = null
            phase = CapturePhase.IDLE
        }
    }

    @Synchronized fun isPending(mode: CaptureMode) =
        owner == mode && phase == CapturePhase.CONSENT_PENDING

    @Synchronized fun busy(): Boolean = owner != null

    @Synchronized fun state(): Pair<CaptureMode?, CapturePhase> = owner to phase
}
