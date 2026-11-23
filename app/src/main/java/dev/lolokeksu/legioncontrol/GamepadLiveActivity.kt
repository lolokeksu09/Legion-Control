package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * An explicit controller test for THIS activity only. No global input hooks,
 * AccessibilityService, event injection, or capture when activity is hidden.
 */
class GamepadLiveActivity : Activity() {
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
    private lateinit var eventView: TextView
    private var lastMotionShownMs = 0L
    private val recentKeys = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(18.dp, 20.dp, 18.dp, 24.dp)
            isFocusableInTouchMode = true
        }
        root.addView(TextView(this).apply {
            text = "GAMEPAD LIVE"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null,Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Подключи контроллер и нажимай кнопки / двигай стики. " +
                "Показаны только события, полученные этим экраном; нажатие «Назад» выходит."
            textSize = 14f
            setTextColor(Color.LTGRAY)
        })
        eventView = TextView(this).apply {
            textSize = 16f
            setTextColor(LegionUiSupport.ACCENT)
            setLineSpacing(5f,1f)
            text = "Событий нет.\n" + ControllerInventory.format(ControllerInventory.connected())
            setTextIsSelectable(true)
        }
        root.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Обновить список контроллеров"
            isAllCaps = false
            setOnClickListener {
                eventView.text = ControllerInventory.format(ControllerInventory.connected())
            }
        })
        root.addView(eventView)
        root.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Закончить тест"
            setOnClickListener { finish() }
        })
        LegionUiSupport.setModuleActivityContent(this, ScrollView(this).apply {
            addView(root)
        }, "ПРОВЕРКА ГЕЙМПАДА")
        root.requestFocus()
    }

    private fun isController(id: Int): Boolean {
        val d = InputDevice.getDevice(id) ?: return false
        return (d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK &&
            isController(event.deviceId) &&
            (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
            if (event.repeatCount == 0) {
                recentKeys.addFirst(
                    (if (event.action == KeyEvent.ACTION_DOWN) "DOWN " else "UP ") +
                        KeyEvent.keyCodeToString(event.keyCode)
                )
                while (recentKeys.size > 12) recentKeys.removeLast()
                eventView.text = "GAMEPAD LIVE · кнопки этого Activity\n" +
                    recentKeys.joinToString("\n")
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 &&
            event.actionMasked == MotionEvent.ACTION_MOVE &&
            isController(event.deviceId)) {
            if (SystemClock.uptimeMillis() - lastMotionShownMs >= 70L) {
                lastMotionShownMs = SystemClock.uptimeMillis()
                eventView.text = ControllerInventory.axes(event)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
