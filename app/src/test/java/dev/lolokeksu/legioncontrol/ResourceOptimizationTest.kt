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
    @Test fun irrelevantMeasurementsDoNotRecreateGamesOrTools() {
        val old=HubLoadState.Ready(HubSnapshot.EMPTY)
        val new=HubLoadState.Ready(HubSnapshot.EMPTY.copy(batteryPercent=60,refreshHz=120f))
        assertTrue(HubRenderPolicy.equivalent(HubTab.TOOLS,old,new))
        assertTrue(HubRenderPolicy.equivalent(HubTab.GAMES,old,new))
        assertFalse(HubRenderPolicy.equivalent(HubTab.HOME,old,new))
        assertFalse(HubRenderPolicy.equivalent(HubTab.SETTINGS,old,new))
        val app=HubApp("com.example.game","Game",true,false,true,false)
        val games=HubLoadState.Ready(HubSnapshot.EMPTY.copy(apps=listOf(app)))
        assertFalse(HubRenderPolicy.equivalent(HubTab.GAMES,old,games))
        assertTrue(HubRenderPolicy.equivalent(HubTab.TOOLS,old,games))
        val warning=HubLoadState.Ready(HubSnapshot.EMPTY.copy(warnings=listOf("warning")))
        assertFalse(HubRenderPolicy.equivalent(HubTab.TOOLS,old,warning))
        assertFalse(HubRenderPolicy.equivalent(HubTab.GAMES,old,HubLoadState.Failed("error")))
        assertFalse(HubRenderPolicy.equivalent(HubTab.GAMES,null,new))
    }
    @Test fun probeCadenceUsesElapsedClockAndPreservesFirstAttempt() {
        assertTrue(MonotonicProbePolicy.due(null,0,120000))
        assertFalse(MonotonicProbePolicy.due(10,120009,120000))
        assertTrue(MonotonicProbePolicy.due(10,120010,120000))
        assertFalse(MonotonicProbePolicy.due(100,0,120000))
    }
}
