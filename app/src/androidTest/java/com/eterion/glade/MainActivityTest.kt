package com.eterion.glade

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class MainActivityTest {
    @Test fun drawerAndListFillWindowWithUnclippedInsetPadding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            instrumentation.runOnMainSync {
                val host = descendants(activity.window.decorView).filterIsInstance<DrawerHost>().single()
                assertTrue(host.getChildAt(0).performAccessibilityAction(R.id.action_open_drawer, null))
            }
            awaitDrawerOpen(activity)
            instrumentation.runOnMainSync {
                val grid = descendants(activity.window.decorView).filterIsInstance<ListView>().single()
                val drawer = grid.parent.parent as LinearLayout
                val root = drawer.parent as ViewGroup
                assertEquals(0, drawer.left)
                assertEquals(0, drawer.top)
                assertEquals(root.width, drawer.right)
                assertEquals(root.height, drawer.bottom)
                assertEquals(0f, drawer.translationY, 0.01f)
                assertTrue(drawer.clipToOutline)
                val corners = (drawer.background as android.graphics.drawable.GradientDrawable).cornerRadii!!
                assertTrue(corners[0] > 0f && corners[2] > 0f)
                assertEquals(0f, corners[4], 0f)
                assertEquals(0f, corners[6], 0f)
                @Suppress("DEPRECATION")
                assertEquals(android.graphics.Color.TRANSPARENT, activity.window.statusBarColor)
                @Suppress("DEPRECATION")
                assertEquals(android.graphics.Color.TRANSPARENT, activity.window.navigationBarColor)
                assertFalse(activity.window.isStatusBarContrastEnforced)
                assertFalse(activity.window.isNavigationBarContrastEnforced)
                val insets = root.rootWindowInsets
                assertEquals(0, drawer.paddingTop)
                assertEquals(0, drawer.paddingBottom)
                assertFalse(grid.clipToPadding)
                val location = IntArray(2)
                grid.getLocationOnScreen(location)
                assertEquals(0, location[1])
                assertEquals(root.height, grid.height)
                @Suppress("DEPRECATION")
                assertTrue("Initial top padding should clear the status bar", grid.paddingTop >= insets.systemWindowInsetTop)
                @Suppress("DEPRECATION")
                assertTrue("End padding should allow the last row to clear navigation", grid.paddingBottom >= insets.systemWindowInsetBottom)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun drawerHasSpacedRowsLabelsAndAccessibilityWithoutSearch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            var grid: ListView? = null
            instrumentation.runOnMainSync {
                val views = descendants(activity.window.decorView)
                val host = views.filterIsInstance<DrawerHost>().single()
                assertTrue(host.getChildAt(0).performAccessibilityAction(R.id.action_open_drawer, null))
                grid = views.filterIsInstance<ListView>().single()
            }
            awaitDrawerOpen(activity)
            val deadline = SystemClock.uptimeMillis() + 5000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { ready = grid!!.count > 0 && grid!!.childCount > 0 }
                if (!ready) SystemClock.sleep(25)
            }
            assertTrue("App catalog did not load", ready)
            instrumentation.runOnMainSync {
                val firstRow = grid!!.getChildAt(0) as LinearLayout
                val first = firstRow.getChildAt(0)
                val firstLabel = first.contentDescription.toString()
                val node = first.createAccessibilityNodeInfo()
                val visibleRect = android.graphics.Rect()
                val globalVisible = first.getGlobalVisibleRect(visibleRect)
                assertTrue("First app tile must be visible to accessibility: $firstLabel; ${first.width}x${first.height}, shown=${first.isShown}, alpha=${first.alpha}, global=$globalVisible $visibleRect, important=${first.importantForAccessibility}", node.isVisibleToUser)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, first.importantForAccessibility)
                assertTrue(descendants(activity.window.decorView).filterIsInstance<EditText>().isEmpty())
                assertTrue(descendants(first).filterIsInstance<TextView>().any { it.text.toString() == firstLabel })
                assertEquals(5, firstRow.childCount)
                if (grid!!.childCount > 1) {
                    val below = grid!!.getChildAt(1)
                    val pitchDp = (below.top - firstRow.top) / activity.resources.displayMetrics.density
                    assertEquals("Original row pitch should be restored", 106f, pitchDp, 1f)
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun homeGridReachesBottomAndDrawerHasNoHeaderControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val views = descendants(activity.window.decorView)
                val grid = views.filterIsInstance<android.widget.GridLayout>().single()
                val home = grid.parent as ViewGroup
                assertEquals(7, grid.rowCount)
                assertEquals(home.height - home.paddingBottom, grid.bottom)
                assertFalse(views.filterIsInstance<TextView>().any { it.text.toString() in listOf("⌃", "All apps", "×") })
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun awaitDrawerOpen(activity: android.app.Activity) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 2000
        var open = false
        while (!open && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                open = descendants(activity.window.decorView).filterIsInstance<DrawerHost>().single().progress == 1f
            }
            if (!open) SystemClock.sleep(20)
        }
        assertTrue("Drawer did not finish opening", open)
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
