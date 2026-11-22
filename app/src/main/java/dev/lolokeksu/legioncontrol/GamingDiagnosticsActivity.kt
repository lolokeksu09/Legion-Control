package dev.lolokeksu.legioncontrol

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

/** Compatibility explicit Intent entry point for canonical Gaming Diagnostics Fragment. */
class GamingDiagnosticsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val container=FrameLayout(this).apply { id=R.id.hub_nav_host_container }
        setContentView(container)
        if(savedInstanceState==null) supportFragmentManager.beginTransaction()
            .replace(container.id,GamingDiagnosticsFragment()).commitNow()
    }
}
