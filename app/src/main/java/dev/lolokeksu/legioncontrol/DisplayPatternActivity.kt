package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager

/**
 * Manual solid-color panel check: NO automatic dead-pixel inference.
 * Only this Activity changes its full-screen content; Android brightness
 * and global display settings remain under user/OEM control.
 */
class DisplayPatternActivity : Activity() {
    private val patterns = intArrayOf(
        Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.GRAY
    )
    private var index = 0
    private lateinit var surface: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        surface = View(this).apply {
            isClickable = true
            setBackgroundColor(patterns[index])
            setOnClickListener { next() }
        }
        setContentView(surface)
    }

    private fun next() {
        index++
        if (index >= patterns.size) {
            finish()
            return
        }
        surface.setBackgroundColor(patterns[index])
    }
}
