package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SmartRulesTest {
    private fun point(
        time:Long=1000L, percent:Int?=null,temp:Int?=null,thermal:Int?=null,
        saver:Boolean?=null,net:String="WIFI",validated:Boolean?=true
    )=GameTelemetryPoint(time,percent,temp,null,thermal,saver,null,net,validated)

    private fun rule(id:Long=1,kind:SmartRuleKind=SmartRuleKind.BATTERY_HOT,
                     threshold:Int=420,enabled:Boolean=true,cooldown:Long=120_000
    )=SmartRule(id,"com.example.game",kind,threshold,cooldown,enabled)

    @Test fun hotThresholdIsInclusiveAndOneAlertPerActivePeriod() {
        val engine=SmartRulesEngine()
        val r=rule()
        assertTrue(engine.evaluate(point(1000,temp=419),listOf(r)).isEmpty())
        val hit=engine.evaluate(point(2000,temp=420),listOf(r))
        assertEquals(SmartRuleKind.BATTERY_HOT,hit.single().kind)
        assertTrue(engine.evaluate(point(3000,temp=500),listOf(r)).isEmpty())
    }

    @Test fun cooldownBlocksRecoverySpikeAndAllowsLaterActivation() {
        val engine=SmartRulesEngine()
        val r=rule()
        assertEquals(1,engine.evaluate(point(1000,temp=450),listOf(r)).size)
        engine.evaluate(point(2000,temp=390),listOf(r))
        assertTrue(engine.evaluate(point(3000,temp=455),listOf(r)).isEmpty())
        engine.evaluate(point(121000,temp=380),listOf(r))
        assertEquals(1,engine.evaluate(point(122000,temp=450),listOf(r)).size)
    }

    @Test fun unknownMeasurementsAreNotZeroOrFalseAlerts() {
        val engine=SmartRulesEngine()
        val rules=listOf(rule(1,SmartRuleKind.BATTERY_LOW,20),
            rule(2,SmartRuleKind.BATTERY_HOT,420),
            rule(3,SmartRuleKind.THERMAL_SEVERE,3),
            rule(4,SmartRuleKind.NETWORK_UNVALIDATED,1))
        assertTrue(engine.evaluate(point(percent=null,temp=null,thermal=null,validated=null),rules).isEmpty())
        assertTrue(engine.evaluate(point(2000,percent=40,temp=390,thermal=0,validated=true),rules).isEmpty())
    }

    @Test fun batteryAndThermalSignalsUseTheirOwnSources() {
        val engine=SmartRulesEngine()
        val rules=listOf(rule(1,SmartRuleKind.BATTERY_LOW,20),
            rule(2,SmartRuleKind.THERMAL_SEVERE,3))
        val hits=engine.evaluate(point(percent=20,thermal=3),rules)
        assertEquals(setOf(SmartRuleKind.BATTERY_LOW,SmartRuleKind.THERMAL_SEVERE),
            hits.map { it.kind }.toSet())
    }

    @Test fun networkChangeNeedsTwoActualNetworkSamples() {
        val engine=SmartRulesEngine()
        val r=rule(1,SmartRuleKind.NETWORK_CHANGED,1)
        assertTrue(engine.evaluate(point(net="UNKNOWN"),listOf(r)).isEmpty())
        assertTrue(engine.evaluate(point(2000,net="WIFI"),listOf(r)).isEmpty())
        assertTrue(engine.evaluate(point(3000,net="WIFI"),listOf(r)).isEmpty())
        assertEquals(1,engine.evaluate(point(4000,net="CELLULAR"),listOf(r)).size)
        assertTrue(engine.evaluate(point(5000,net="CELLULAR"),listOf(r)).isEmpty())
    }

    @Test fun disabledRuleCannotFire() {
        val engine=SmartRulesEngine()
        assertTrue(engine.evaluate(point(temp=500),listOf(rule(enabled=false))).isEmpty())
    }

    @Test fun settingsValidationRejectsInvalidThresholdAndCooldown() {
        assertThrows(IllegalArgumentException::class.java) {
            rule(threshold=200)
        }
        assertThrows(IllegalArgumentException::class.java) {
            rule(cooldown=1000)
        }
    }

    @Test fun summaryDoesNotClaimGameFpsOrInventData() {
        val s=SmartInsightsResult("com.example.game",7,2,1,18,80,425,3,5)
        val report=s.textReport()
        assertTrue(report.contains("18 мин"))
        assertTrue(report.contains("42.5 °C"))
        assertTrue(report.contains("не"))
        assertFalse(report.contains("120 FPS"))
    }
}
