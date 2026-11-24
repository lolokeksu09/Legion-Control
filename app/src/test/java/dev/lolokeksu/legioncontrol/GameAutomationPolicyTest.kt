package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class GameAutomationPolicyTest {
    private fun e(t:Long,s:AutomationSignal)=AutomationEvent(t,s)
    private val resume=AutomationSignal.TARGET_RESUMED
    private val exit=AutomationSignal.TARGET_EXIT
    private val other=AutomationSignal.OTHER_RESUMED
    @Test fun oldOrAmbiguousEventsCannotApply() {
        assertEquals(AutomationDecision.WAIT,GameAutomationPolicy.decide(listOf(e(90,resume)),100,99,110,false))
        assertEquals(AutomationDecision.WAIT,GameAutomationPolicy.decide(listOf(e(105,resume),e(105,other)),100,99,110,false))
        assertEquals(AutomationDecision.WAIT,GameAutomationPolicy.decide(listOf(e(105,resume)),100,105,110,false))
        assertEquals(AutomationDecision.WAIT,GameAutomationPolicy.decide(listOf(e(105,resume)),100,99,9000,false))
    }
    @Test fun onlyFreshObservedTargetCanApply() {
        assertEquals(AutomationDecision.APPLY,GameAutomationPolicy.decide(listOf(e(105,resume)),100,99,110,false))
        assertEquals(AutomationDecision.WAIT,GameAutomationPolicy.decide(listOf(e(105,resume),e(106,exit)),100,99,110,false))
    }
    @Test fun activeSessionExitsOnOtherAppOrTargetPause() {
        assertEquals(AutomationDecision.FINISH,GameAutomationPolicy.decide(listOf(e(105,exit)),100,99,110,true))
        assertEquals(AutomationDecision.FINISH,GameAutomationPolicy.decide(listOf(e(105,other),e(106,resume)),100,99,110,true))
        assertEquals(AutomationDecision.KEEP,GameAutomationPolicy.decide(listOf(e(105,exit),e(106,resume)),100,99,110,true))
        assertEquals(AutomationDecision.KEEP,GameAutomationPolicy.decide(emptyList(),100,99,110,true))
    }
}
