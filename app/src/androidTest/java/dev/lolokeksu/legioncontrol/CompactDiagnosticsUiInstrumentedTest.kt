package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactDiagnosticsUiInstrumentedTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density+0.5f).toInt()
    private fun measure(v:View,c:Context,width:Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(dp(c,width),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        v.layout(0,0,v.measuredWidth,v.measuredHeight)
    }
    @Test fun normalRowStacksAtNarrowWidthOrLargeFontAndPreservesClicks() {
        inst.runOnMainSync {
            val c=inst.targetContext.createConfigurationContext(Configuration(inst.targetContext.resources.configuration).apply{fontScale=1f})
            var clicks=0
            val a=Button(c).apply{text="Сравнить A/B";setOnClickListener{clicks++}}
            val b=Button(c).apply{text="История"}
            val row=CompactActionRow(c,a,b)
            LegionUiSupport.styleModuleTree(row)
            measure(row,c,340)
            assertEquals(LinearLayout.HORIZONTAL,row.orientation)
            assertTrue(a.measuredHeight>=dp(c,48));assertTrue(b.measuredHeight>=dp(c,48))
            assertTrue(a.right<=b.left || b.right<=a.left)
            a.performClick();assertEquals(1,clicks)
            measure(row,c,260);assertEquals(LinearLayout.VERTICAL,row.orientation)
            assertTrue(a.bottom<=b.top)
            val large=c.createConfigurationContext(Configuration(c.resources.configuration).apply{fontScale=1.5f})
            val stacked=CompactActionRow(large,Button(large),Button(large))
            measure(stacked,large,340);assertEquals(LinearLayout.VERTICAL,stacked.orientation)
        }
    }
    @Test fun onlyVisibleControlsUseSpaceAndBothPanelsMeasure() {
        inst.runOnMainSync {
            val c=inst.targetContext.createConfigurationContext(Configuration(inst.targetContext.resources.configuration).apply{fontScale=1f})
            val a=Button(c);val b=Button(c);val stop=Button(c).apply{visibility=View.GONE}
            val row=CompactActionRow(c,a,b,stop)
            measure(row,c,340);assertEquals(LinearLayout.HORIZONTAL,row.orientation)
            b.visibility=View.GONE
            measure(row,c,340);assertEquals(LinearLayout.VERTICAL,row.orientation)
            assertEquals(row.measuredWidth,a.measuredWidth)
            val network=NetworkLivePanel(c,{},{})
            val battery=BatteryLabPanel(c,{})
            try {
                LegionUiSupport.styleModuleTree(network);LegionUiSupport.styleModuleTree(battery)
                measure(network,c,340);measure(battery,c,340)
                assertTrue(network.measuredHeight>0);assertTrue(battery.measuredHeight>0)
                assertEquals(1,(0 until network.childCount).map{network.getChildAt(it)}
                    .count{it.javaClass.simpleName=="LiveLatencyChart" && it.visibility==View.VISIBLE})
            } finally {network.close();battery.close()}
        }
    }
}
