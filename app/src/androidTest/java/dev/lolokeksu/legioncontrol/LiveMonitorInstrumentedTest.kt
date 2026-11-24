package dev.lolokeksu.legioncontrol

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveMonitorInstrumentedTest {
    @Test fun finderUsesIdentityNotLabelAndNeverClicks() {
        ActivityScenario.launch(LiveMonitorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = android.widget.LinearLayout(activity)
                val target = android.widget.Button(activity).apply { text = "Original title" }
                val decoy = android.widget.Button(activity).apply { text = "Original title" }
                var clicks = 0
                target.setOnClickListener { clicks++ }
                decoy.setOnClickListener { clicks++ }
                root.addView(decoy)
                root.addView(target)
                val feature = requireNotNull(FeatureFinder.resolve("monitor.start", FeatureModule.MONITOR))
                assertNull(FeatureReveal.findTarget(root, feature))
                FeatureReveal.bind(target, feature.id)
                assertSame(target, FeatureReveal.findTarget(root, feature))
                target.text = "Changed title"
                assertSame(target, FeatureReveal.findTarget(root, feature))
                target.isEnabled = false
                assertSame(target, FeatureReveal.findTarget(root, feature))
                FeatureReveal.bind(decoy, feature.id)
                assertNull("Duplicate visible IDs must fail closed", FeatureReveal.findTarget(root, feature))
                decoy.visibility = android.view.View.GONE
                assertSame(target, FeatureReveal.findTarget(root, feature))
                assertEquals(0, clicks)
            }
        }
    }

    @Test fun sharedButtonUsesCompactMinimumWithoutFixedHeight() {
        ActivityScenario.launch(LiveMonitorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = android.widget.Button(activity).apply { text = "Long action label" }
                LegionUiSupport.styleAction(button)
                val expected = (48 * activity.resources.displayMetrics.density + 0.5f).toInt()
                assertEquals(expected, button.minHeight)
                assertEquals(expected, button.minimumHeight)
                assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, button.layoutParams.height)
                assertFalse(button.isAllCaps)
            }
        }
    }

    @Test fun sharedDialogsPreserveConfirmationAndChoiceSemantics() {
        ActivityScenario.launch(LiveMonitorActivity::class.java).use { scenario ->
            val idle = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            lateinit var dialog: android.app.AlertDialog
            var confirmed = 0
            var picked = -1
            val flags = booleanArrayOf(false, true)
            var saved: BooleanArray? = null
            lateinit var field: android.widget.EditText
            scenario.onActivity { activity ->
                dialog = LegionDialogs.builder(activity).setTitle("Подтверждение")
                    .setMessage("Применить изменение?")
                    .setPositiveButton("Применить") { _, _ -> confirmed++ }
                    .setNegativeButton("Отмена", null).show()
            }
            idle.waitForIdleSync()
            scenario.onActivity {
                assertTrue(dialog.isShowing)
                assertEquals(14f, dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                    .textSize / it.resources.displayMetrics.scaledDensity, 1f)
                dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
            }
            idle.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0, confirmed)
                assertFalse(dialog.isShowing)
                dialog = LegionDialogs.builder(activity).setTitle("Выбор")
                    .setItems(arrayOf("Первый", "Второй")) { _, index -> picked = index }
                    .setNegativeButton("Отмена", null).show()
            }
            idle.waitForIdleSync()
            scenario.onActivity {
                val list = dialog.listView
                assertTrue(list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1)))
            }
            idle.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(1, picked)
                assertFalse(dialog.isShowing)
                dialog = LegionDialogs.builder(activity).setTitle("Один вариант")
                    .setSingleChoiceItems(arrayOf("Первый", "Второй"), 0) { source, index ->
                        picked = index
                        source.dismiss()
                    }.setNegativeButton("Отмена", null).show()
            }
            idle.waitForIdleSync()
            scenario.onActivity {
                val list = dialog.listView
                assertEquals(0, list.checkedItemPosition)
                list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1))
            }
            idle.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(1, picked)
                assertFalse(dialog.isShowing)
                dialog = LegionDialogs.builder(activity).setTitle("Правила")
                    .setMultiChoiceItems(arrayOf("Первое", "Второе"), flags) { _, i, checked -> flags[i] = checked }
                    .setPositiveButton("Сохранить") { _, _ -> saved = flags.copyOf() }
                    .setNegativeButton("Отмена", null).show()
            }
            idle.waitForIdleSync()
            scenario.onActivity {
                val list = dialog.listView
                assertTrue(list.isItemChecked(1))
                list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                assertNull(saved)
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
            }
            idle.waitForIdleSync()
            scenario.onActivity { activity ->
                assertArrayEquals(booleanArrayOf(true, true), saved)
                assertFalse(dialog.isShowing)
                field = android.widget.EditText(activity).apply { setText("test") }
                dialog = LegionDialogs.builder(activity).setTitle("Ввод")
                    .setView(field)
                    .setPositiveButton("Сохранить") { _, _ ->
                        assertEquals("test", field.text.toString())
                        confirmed++
                    }.setNegativeButton("Отмена", null).create()
                dialog.show()
            }
            idle.waitForIdleSync()
            scenario.onActivity {
                assertTrue(dialog.isShowing)
                assertEquals(android.graphics.Color.WHITE, field.currentTextColor)
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
            }
            idle.waitForIdleSync()
            scenario.onActivity {
                assertEquals(1, confirmed)
                assertFalse(dialog.isShowing)
            }
        }
    }

    @Test fun commonChartAndNoticeComponentsRemainBoundedAndReadable() {
        ActivityScenario.launch(LiveMonitorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val width = (320 * density).toInt()
                val height = (180 * density).toInt()
                val view = android.view.View(activity)
                view.layout(0, 0, width, height)
                val bitmap = android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    val canvas = android.graphics.Canvas(bitmap)
                    val frame = LegionChartFrame(view)
                    frame.empty(canvas)
                    val plot = requireNotNull(frame.axes(canvas, 10.0, 50.0, 120000L, "°C"))
                    assertTrue(plot.left > 0 && plot.right < width && plot.top > 0 && plot.bottom < height)
                    assertEquals(plot.bottom, frame.y(plot,10.0,10.0,50.0),0.01f)
                    assertEquals(plot.top, frame.y(plot,50.0,10.0,50.0),0.01f)
                    assertEquals(plot.centerY(), frame.y(plot,35.0,35.0,35.0),0.01f)
                    assertNull(frame.axes(canvas, Double.NaN,50.0,120000L,"°C"))
                    val notice = LegionUiSupport.noticeView(activity,"No records",LegionUiSupport.Notice.EMPTY)
                    assertEquals("No records",notice.text.toString())
                    assertEquals(LegionUiSupport.MUTED,notice.currentTextColor)
                    LegionUiSupport.notice(notice,LegionUiSupport.Notice.ERROR)
                    assertEquals("No records",notice.text.toString())
                    assertEquals(LegionUiSupport.DANGER,notice.currentTextColor)
                    assertEquals(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE,notice.accessibilityLiveRegion)
                    val spinner = LegionUiSupport.spinner(activity,listOf("First","Second"))
                    spinner.setSelection(1)
                    assertEquals("Second",spinner.selectedItem.toString())
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun realReadingsStopWhenScreenStopsAndRequireExplicitRestart() {
        ActivityScenario.launch(LiveMonitorActivity::class.java).use { scenario ->
            lateinit var model: LiveMonitorViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity)[LiveMonitorViewModel::class.java]
                assertFalse(model.state.value!!.running)
                assertTrue(model.state.value!!.points.isEmpty())
                model.interval(2000)
                model.start()
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 20000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                var ready = false
                scenario.onActivity { ready = model.state.value!!.points.size >= 2 }
                if (ready) break
                Thread.sleep(250)
            }
            scenario.onActivity {
                val points = model.state.value!!.points
                assertTrue("Actual periodic readings required", points.size >= 2)
                assertTrue("Expected real Android battery or RAM/display reading", points.any {
                    it.battery != null || it.availableRamBytes != null || it.displayHz != null
                })
                assertTrue(points.zipWithNext().all { (a,b) -> b.elapsedMs >= a.elapsedMs })
                model.pinBaseline()
                assertEquals(points.last(), model.state.value!!.baseline)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            var count = -1
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertFalse("onStop must halt collection", model.state.value!!.running)
                count = model.state.value!!.points.size
            }
            Thread.sleep(4500)
            scenario.onActivity {
                assertEquals("No implicit restart or late publication", count, model.state.value!!.points.size)
                assertFalse(model.state.value!!.running)
                assertTrue(model.beginExport())
                assertFalse(model.beginExport())
                model.start()
                assertFalse("Pending picker must preserve captured run", model.state.value!!.running)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val retained = ViewModelProvider(activity)[LiveMonitorViewModel::class.java]
                assertSame(model, retained)
                assertTrue(retained.hasPendingDocument)
                assertEquals(count, retained.state.value!!.points.size)
                retained.finishExport(null)
                assertFalse(retained.hasPendingDocument)
                assertTrue("Cancel permits retry", retained.beginExport())
                retained.finishExport(null)
                model.start()
                assertTrue(model.state.value!!.running)
                assertNotNull("Pinned baseline survives a new run", model.state.value!!.baseline)
                model.stop()
                assertFalse(model.state.value!!.running)
            }
        }
    }
}
