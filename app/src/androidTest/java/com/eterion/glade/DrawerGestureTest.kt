package com.eterion.glade

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class DrawerGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun slowDragFollowsFingerCanReverseAndCancelsBackHome() = withActivity { activity, host ->
        val height = host.height.toFloat()
        val x = host.width / 2f
        val start = height * 0.85f
        val downTime = SystemClock.uptimeMillis()
        touch(activity, downTime, MotionEvent.ACTION_DOWN, x, start)
        move(activity, downTime, x, start, start - height * 0.35f)
        val held = progress(host)
        assertEquals(0.35f, held, 0.03f)
        instrumentation.runOnMainSync {
            val drawer = host.getChildAt(1) as ViewGroup
            val homeGrid = descendants(host.getChildAt(0)).filterIsInstance<android.widget.GridLayout>().single()
            assertTrue(drawer.alpha > held && drawer.alpha < 1f)
            assertTrue(homeGrid.translationY < 0f)
            assertTrue(homeGrid.alpha in 0f..0.99f)
            assertEquals(0f, drawer.getChildAt(0).alpha, 0f)
        }
        SystemClock.sleep(150) // Still held: the surface must not animate ahead of the finger.
        assertEquals(held, progress(host), 0.001f)
        move(activity, downTime, x, start - height * 0.35f, start - height * 0.12f)
        assertEquals(0.12f, progress(host), 0.03f)
        instrumentation.runOnMainSync { assertTrue(host.getChildAt(1).alpha in 0.01f..0.4f) }
        touch(activity, downTime, MotionEvent.ACTION_CANCEL, x, start - height * 0.12f)
        await { progress(host) == 0f }
        instrumentation.runOnMainSync {
            val homeGrid = descendants(host.getChildAt(0)).filterIsInstance<android.widget.GridLayout>().single()
            assertEquals(1f, homeGrid.alpha, 0f)
            assertEquals(0f, homeGrid.translationY, 0f)
        }
    }

    @Test fun slowReleaseOpensThenDownwardTopEdgeDragCloses() = withActivity { activity, host ->
        val height = host.height.toFloat()
        val x = host.width / 2f
        var downTime = SystemClock.uptimeMillis()
        touch(activity, downTime, MotionEvent.ACTION_DOWN, x, height * 0.9f)
        move(activity, downTime, x, height * 0.9f, height * 0.2f)
        instrumentation.runOnMainSync {
            val drawer = host.getChildAt(1) as ViewGroup
            assertTrue("Apps should start appearing after the threshold", drawer.getChildAt(0).alpha > 0f)
        }
        SystemClock.sleep(150)
        touch(activity, downTime, MotionEvent.ACTION_UP, x, height * 0.2f)
        await { progress(host) == 1f }
        instrumentation.runOnMainSync {
            val drawer = host.getChildAt(1) as ViewGroup
            assertEquals(1f, drawer.getChildAt(0).alpha, 0f)
        }
        var headerY = 0f
        instrumentation.runOnMainSync {
            @Suppress("DEPRECATION")
            headerY = host.rootWindowInsets.systemWindowInsetTop + 6f * activity.resources.displayMetrics.density
        }
        downTime = SystemClock.uptimeMillis()
        touch(activity, downTime, MotionEvent.ACTION_DOWN, x, headerY)
        move(activity, downTime, x, headerY, headerY + height * 0.65f)
        assertEquals(0.35f, progress(host), 0.03f)
        SystemClock.sleep(150)
        touch(activity, downTime, MotionEvent.ACTION_UP, x, headerY + height * 0.65f)
        await { progress(host) == 0f }
    }

    @Test fun firstSmallMeaningfulMoveImmediatelyMovesTheDrawer() = withActivity { activity, host ->
        val x = host.width / 2f
        val y = host.height * 0.85f
        val delta = ViewConfiguration.get(activity).scaledTouchSlop * 0.75f
        val downTime = SystemClock.uptimeMillis()
        touch(activity, downTime, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(20)
        touch(activity, downTime, MotionEvent.ACTION_MOVE, x, y - delta)
        assertTrue("The first movement must update the drawer without waiting for another MOVE", progress(host) > 0f)
        touch(activity, downTime, MotionEvent.ACTION_CANCEL, x, y - delta)
        await { progress(host) == 0f }
    }

    @Test fun downwardDragInScrolledGridScrollsAppsInsteadOfClosingDrawer() = withActivity { activity, host ->
        var grid: ListView? = null
        instrumentation.runOnMainSync { host.open(false); grid = descendants(host).filterIsInstance<ListView>().single() }
        await { var ready = false; instrumentation.runOnMainSync { ready = grid!!.count > 0 }; ready }
        instrumentation.runOnMainSync {
            grid!!.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, (0..199).map { "App $it" })
            grid!!.setSelection(100)
        }
        await { var scrolled = false; instrumentation.runOnMainSync { scrolled = grid!!.canScrollVertically(-1) }; scrolled }
        var before = 0
        instrumentation.runOnMainSync { before = grid!!.firstVisiblePosition }
        val x = host.width / 2f
        val start = host.height * 0.45f
        val end = start + host.height * 0.2f
        val downTime = SystemClock.uptimeMillis()
        touch(activity, downTime, MotionEvent.ACTION_DOWN, x, start)
        move(activity, downTime, x, start, end)
        touch(activity, downTime, MotionEvent.ACTION_UP, x, end)
        assertEquals(1f, progress(host), 0f)
        instrumentation.runOnMainSync { assertTrue("App list did not scroll", grid!!.firstVisiblePosition < before) }
        instrumentation.runOnMainSync { host.close(false); host.open(false) }
        await {
            var atTop = false
            instrumentation.runOnMainSync { atTop = grid!!.firstVisiblePosition == 0 }
            atTop
        }
    }

    @Test fun scrolledContentGetsTranslucentSystemBarProtection() = withActivity { activity, host ->
        lateinit var list: ListView
        instrumentation.runOnMainSync { host.open(false); list = descendants(host).filterIsInstance<ListView>().single() }
        await { var ready = false; instrumentation.runOnMainSync { ready = list.count > 0 }; ready }
        instrumentation.runOnMainSync {
            val overlay = android.graphics.Bitmap.createBitmap(host.width, host.height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                host.foreground.draw(android.graphics.Canvas(overlay))
                assertEquals("No status scrim before rows reach it", 0, Color.alpha(overlay.getPixel(host.width / 2, 8)))
            } finally { overlay.recycle() }
        }
        instrumentation.runOnMainSync {
            list.adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, (0..199).map { "Row $it" }) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getView(position, convertView, parent).apply {
                        setBackgroundColor(Color.RED)
                        minimumHeight = (80 * resources.displayMetrics.density).toInt()
                    }
            }
            list.setSelection(100)
        }
        await { var ready = false; instrumentation.runOnMainSync { ready = list.firstVisiblePosition > 0 }; ready }
        instrumentation.runOnMainSync {
            @Suppress("DEPRECATION")
            val statusInset = host.rootWindowInsets.systemWindowInsetTop
            list.scrollListBy(list.paddingTop + statusInset / 2)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(240)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        try {
            val x = (40 * activity.resources.displayMetrics.density).toInt()
            val statusPixel = screenshot.getPixel(x, 8)
            val navigationPixel = screenshot.getPixel(x, screenshot.height - 8)
            val surface = activity.getColor(R.color.drawer_surface)
            for (pixel in listOf(statusPixel, navigationPixel)) {
                val expectedRed = (Color.red(surface) * 0.8f + 255 * 0.2f).toInt()
                val expectedGreen = (Color.green(surface) * 0.8f).toInt()
                assertTrue("Translucent scrim should protect controls while retaining content", kotlin.math.abs(Color.red(pixel) - expectedRed) < 10 && kotlin.math.abs(Color.green(pixel) - expectedGreen) < 10)
            }
            assertEquals("Rows still render beneath the scrim", Color.RED, screenshot.getPixel(x, screenshot.height / 2))
        } finally { screenshot.recycle() }
        instrumentation.runOnMainSync {
            val overlay = android.graphics.Bitmap.createBitmap(host.width, host.height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                host.foreground.draw(android.graphics.Canvas(overlay))
                val x = host.width / 2
                val density = activity.resources.displayMetrics.density
                @Suppress("DEPRECATION")
                val inset = host.rootWindowInsets.systemWindowInsetTop
                val edge = Color.alpha(overlay.getPixel(x, 8))
                val feather = Color.alpha(overlay.getPixel(x, inset + (12 * density).toInt()))
                assertTrue("Background feathers smoothly into content", feather in 1 until edge)
                assertEquals("No hard rectangle past the gradient", 0, Color.alpha(overlay.getPixel(x, inset + (25 * density).toInt())))
            } finally { overlay.recycle() }
        }
        instrumentation.runOnMainSync { list.setSelection(0) }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(240)
        instrumentation.runOnMainSync {
            val overlay = android.graphics.Bitmap.createBitmap(host.width, host.height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                host.foreground.draw(android.graphics.Canvas(overlay))
                assertEquals("Top stays clear at the start of the list", 0, Color.alpha(overlay.getPixel(host.width / 2, 8)))
                assertTrue("Bottom is protected independently", Color.alpha(overlay.getPixel(host.width / 2, host.height - 8)) > 0)
            } finally { overlay.recycle() }
        }
        instrumentation.runOnMainSync {
            host.close(false)
            val overlay = android.graphics.Bitmap.createBitmap(host.width, host.height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                host.foreground.draw(android.graphics.Canvas(overlay))
                assertEquals(0, Color.alpha(overlay.getPixel(host.width / 2, 8)))
                assertEquals(0, Color.alpha(overlay.getPixel(host.width / 2, host.height - 8)))
            } finally { overlay.recycle() }
        }
    }

    @Test fun lightAndDarkPalettesAreNeutralWithOppositeContrast() = withActivity { activity, _ ->
        for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration(activity.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            }
            val context = activity.createConfigurationContext(config)
            val surface = context.getColor(R.color.drawer_surface)
            val foreground = context.getColor(R.color.drawer_foreground)
            val secondary = context.getColor(R.color.drawer_secondary)
            for (color in listOf(surface, foreground, secondary)) {
                assertEquals(Color.red(color), Color.green(color))
                assertEquals(Color.green(color), Color.blue(color))
            }
            assertTrue(Color.alpha(surface) in 220..250)
            if (mode == Configuration.UI_MODE_NIGHT_NO) assertTrue(Color.red(surface) > Color.red(foreground))
            else assertTrue(Color.red(surface) < Color.red(foreground))
        }
    }

    private fun withActivity(block: (Activity, DrawerHost) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            lateinit var host: DrawerHost
            instrumentation.runOnMainSync { host = descendants(activity.window.decorView).filterIsInstance<DrawerHost>().single() }
            await { host.height > 0 }
            block(activity, host)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun touch(activity: Activity, downTime: Long, action: Int, x: Float, y: Float) {
        instrumentation.runOnMainSync {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            activity.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun move(activity: Activity, downTime: Long, x: Float, from: Float, to: Float) {
        for (step in 1..12) {
            SystemClock.sleep(25)
            touch(activity, downTime, MotionEvent.ACTION_MOVE, x, from + (to - from) * step / 12f)
        }
    }

    private fun progress(host: DrawerHost): Float {
        var value = 0f
        instrumentation.runOnMainSync { value = host.progress }
        return value
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        assertTrue("Timed out waiting for drawer state", condition())
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
