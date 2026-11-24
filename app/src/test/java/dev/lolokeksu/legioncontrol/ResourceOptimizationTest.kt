package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ResourceOptimizationTest {
    @Test fun automaticRefreshCoalescesButExplicitRetrySurvives() {
        val g=HubRefreshGate()
        assertTrue(g.request(0,false))
        assertFalse(g.request(1,false))
        repeat(10){assertFalse(g.request(2,true))}
        assertTrue(g.complete(3,true))
        assertFalse(g.complete(4,true))
        assertFalse(g.request(5,false))
        assertTrue(g.request(5,true))
        assertFalse(g.complete(6,false))
        assertTrue(g.request(7,false))
        g.close()
        assertFalse(g.complete(8,true))
        assertFalse(g.request(9,true))
    }
    @Test fun automaticRefreshResumesAfterShortWindow() {
        val g=HubRefreshGate()
        assertTrue(g.request(100,false));assertFalse(g.complete(120,true))
        assertFalse(g.request(869,false));assertTrue(g.request(870,false))
    }
    @Test fun duplicateCallbacksDoNotDropTransitionsOrReconnects() {
        val g=DistinctObservation<Pair<Long,Boolean>>()
        assertTrue(g.accept(1L to true));assertFalse(g.accept(1L to true))
        assertTrue(g.accept(1L to false));assertTrue(g.accept(2L to false))
        g.clearIf{it.first==1L};assertFalse(g.accept(2L to false))
        g.clearIf{it.first==2L};assertTrue(g.accept(2L to false))
    }
    @Test fun probeCadenceUsesElapsedClockAndPreservesFirstAttempt() {
        assertTrue(MonotonicProbePolicy.due(null,0,120000))
        assertFalse(MonotonicProbePolicy.due(10,120009,120000))
        assertTrue(MonotonicProbePolicy.due(10,120010,120000))
        assertFalse(MonotonicProbePolicy.due(100,0,120000))
    }
}
