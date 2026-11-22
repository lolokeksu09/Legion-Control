package dev.lolokeksu.legioncontrol

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

/** Legacy explicit Intent and Quick Tile adapter to the sole Smart Profiles UI. */
class SmartProfilesActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val container=FrameLayout(this).apply { id=R.id.hub_nav_host_container }
        setContentView(container)
        if(savedInstanceState==null) {
            supportFragmentManager.beginTransaction()
                .replace(container.id,SmartProfilesFragment().apply { arguments=intent.extras })
                .commitNow()
        }
    }
}
