package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** The public launcher accepts only the request to open Legion Control. */
class NavigationHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, LegionHubActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }
}
