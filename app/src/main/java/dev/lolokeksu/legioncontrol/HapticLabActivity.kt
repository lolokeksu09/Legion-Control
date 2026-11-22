package dev.lolokeksu.legioncontrol

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

/** Legacy explicit-Intent adapter, all haptic UI resides in HapticLabFragment. */
class HapticLabActivity:FragmentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val container=FrameLayout(this).apply { id=R.id.hub_nav_host_container }
        setContentView(container)
        if(savedInstanceState==null) {
            supportFragmentManager.beginTransaction()
                .replace(container.id,HapticLabFragment())
                .commitNow()
        }
    }
}
