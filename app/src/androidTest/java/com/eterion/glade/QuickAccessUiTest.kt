package com.eterion.glade

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class QuickAccessUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun menuPinsPersistsAndRemovesQuickAccessWithoutDuplicatingApps() {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("layout", 0)
        val previous = preferences.getString("quick_access_v1", null)
        val store = LayoutStore(preferences)
        store.writeQuickAccess(emptyList())
        var screen: Activity? = null
        try {
            screen = start()
            val firstScreen = screen
            var label = ""
            instrumentation.runOnMainSync {
                val tile = tiles(firstScreen).first()
                label = tile.contentDescription.toString()
                assertTrue(tile.performLongClick())
            }
            clickMenu(context.getString(R.string.add_quick_access))
            awaitUi { hasDivider(firstScreen) }
            val pinnedIds = store.readQuickAccess()
            assertEquals(1, pinnedIds.size)
            instrumentation.runOnMainSync {
                assertNotNull(firstScreen.findViewById<View>(R.id.quick_access_divider))
                assertFalse(descendants(firstScreen.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Quick Access" })
                assertEquals(1, tiles(firstScreen).count { it.contentDescription.toString() == label })
                assertEquals(label, tiles(firstScreen).first().contentDescription.toString())
                firstScreen.finish()
            }
            screen = start()
            val secondScreen = screen
            awaitUi { hasDivider(secondScreen) }
            assertEquals(pinnedIds, store.readQuickAccess())
            instrumentation.runOnMainSync {
                val tile = tiles(secondScreen).first { it.contentDescription.toString() == label }
                assertTrue(tile.performLongClick())
            }
            clickMenu(context.getString(R.string.remove_quick_access))
            awaitUi { !hasDivider(secondScreen) }
            assertTrue(store.readQuickAccess().isEmpty())
            instrumentation.runOnMainSync {
                assertNull(secondScreen.findViewById<View>(R.id.quick_access_divider))
                assertEquals(1, tiles(secondScreen).count { it.contentDescription.toString() == label })
            }
        } finally {
            screen?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
            val editor = preferences.edit()
            if (previous == null) editor.remove("quick_access_v1") else editor.putString("quick_access_v1", previous)
            editor.commit()
        }
    }

    private fun start(): Activity {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        instrumentation.runOnMainSync { descendants(activity.window.decorView).filterIsInstance<DrawerHost>().single().open(false) }
        awaitUi { tiles(activity).isNotEmpty() }
        return activity
    }

    private fun tiles(activity: Activity): List<LinearLayout> {
        val list = descendants(activity.window.decorView).filterIsInstance<ListView>().single()
        return descendants(list).filterIsInstance<LinearLayout>().filter {
            it.visibility == View.VISIBLE && !it.contentDescription.isNullOrBlank()
        }
    }

    private fun hasDivider(activity: Activity) = activity.findViewById<View>(R.id.quick_access_divider) != null

    private fun clickMenu(text: String) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val node = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString() == text && it.isVisibleToUser }
            if (node != null) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                instrumentation.sendPointerSync(down)
                down.recycle()
                SystemClock.sleep(40)
                val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                instrumentation.sendPointerSync(up)
                up.recycle()
                return
            }
            SystemClock.sleep(30)
        }
        fail("Menu action not found: $text")
    }

    private fun awaitUi(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { ready = condition() }
            if (!ready) SystemClock.sleep(25)
        }
        assertTrue("Timed out waiting for Quick Access UI", ready)
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
