package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.os.Bundle

/** Legacy entry point for the shared Game Toolbox destination. */
class GameToolboxActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleNavigation.forwardLegacyEntry(this)
    }
}
