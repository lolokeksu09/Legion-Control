package dev.lolokeksu.legioncontrol

internal enum class AutomationSignal { TARGET_RESUMED, TARGET_EXIT, OTHER_RESUMED }
internal data class AutomationEvent(val wallMs:Long,val signal:AutomationSignal)
internal enum class AutomationDecision { WAIT, APPLY, KEEP, FINISH }

internal object GameAutomationPolicy {
    const val ARM_MS=90_000L
    const val MAX_MS=7_200_000L
    const val FRESH_MS=8_000L
    const val MAX_POLL_GAP_MS=30_000L

    // No historical event or ambiguous equal-time target/exit event may trigger a write.
    fun decide(events:List<AutomationEvent>,confirmedAt:Long,watermark:Long,now:Long,applied:Boolean):AutomationDecision {
        val eligible=events.filter{it.wallMs>=confirmedAt && it.wallMs>watermark && it.wallMs<=now}
        if(eligible.isEmpty()) return if(applied) AutomationDecision.KEEP else AutomationDecision.WAIT
        val latest=eligible.maxOf{it.wallMs}
        val signals=eligible.filter{it.wallMs==latest}.map{it.signal}.toSet()
        if(applied && eligible.any{it.signal!=AutomationSignal.TARGET_RESUMED}) {
            // A same-package activity transition followed by a fresh resume may continue.
            if(signals==setOf(AutomationSignal.TARGET_RESUMED) &&
                eligible.none{it.signal==AutomationSignal.OTHER_RESUMED}) return AutomationDecision.KEEP
            return AutomationDecision.FINISH
        }
        if(applied)return AutomationDecision.KEEP
        return if(signals==setOf(AutomationSignal.TARGET_RESUMED) && now-latest in 0L..FRESH_MS)
            AutomationDecision.APPLY else AutomationDecision.WAIT
    }
}
