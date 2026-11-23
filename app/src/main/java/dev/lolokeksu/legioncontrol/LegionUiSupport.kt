package dev.lolokeksu.legioncontrol

import android.view.WindowInsets
import android.widget.ScrollView

/** Reusable edge-to-edge/IME handling for legacy Views screens. */
object LegionUiSupport {
    fun applyInsets(scroll: ScrollView) {
        scroll.clipToPadding = false
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                    WindowInsets.Type.ime()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }
}
