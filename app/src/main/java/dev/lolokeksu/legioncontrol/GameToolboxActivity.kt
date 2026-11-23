package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle

/**
 * Backwards-compatible entry point for legacy callers.
 * The interactive Toolbox UI is now a NavHost Fragment, so it inherits
 * the same header, bottom tabs and navigation back stack as the Game Hub.
 */
class GameToolboxActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        startActivity(Intent(this, NavigationHostActivity::class.java).apply {
            putExtra("open_game_toolbox", true)
        })
        finish()
    }
}
