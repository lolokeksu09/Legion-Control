package dev.lolokeksu.legioncontrol

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

/** Preserves explicit Activity navigation while NavHost owns maintained Experiment Lab UI. */
class ExperimentLabActivity:FragmentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val container=FrameLayout(this).apply { id=R.id.hub_nav_host_container }
        setContentView(container)
        if(savedInstanceState==null) {
            supportFragmentManager.beginTransaction()
                .replace(container.id,ExperimentLabFragment().apply { arguments=intent.extras })
                .commitNow()
        }
    }
}
