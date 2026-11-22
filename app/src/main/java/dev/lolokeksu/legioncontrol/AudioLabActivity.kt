package dev.lolokeksu.legioncontrol

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

/** Existing deep link/explicit Intent facade for the NavHost Audio Lab screen. */
class AudioLabActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val container=FrameLayout(this).apply { id=R.id.hub_nav_host_container }
        setContentView(container)
        if(savedInstanceState==null) {
            supportFragmentManager.beginTransaction()
                .replace(container.id,AudioLabFragment())
                .commitNow()
        }
    }
}
