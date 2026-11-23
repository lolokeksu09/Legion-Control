package dev.lolokeksu.legioncontrol

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Retained explicit-Intent entry point. Always opens the module inside
 * NavigationHostActivity so the AMOLED theme, header and five tabs apply.
 * Original Intent extras are forwarded as Navigation destination arguments.
 */
class CompatibilityLabActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleNavigation.forwardLegacyEntry(this)
    }
}
