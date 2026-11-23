package com.eterion.glade

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.widget.ListView

/** Draw over scrolling rows, under system controls, without covering the wallpaper on Home. */
class SystemBarScrim(
    private val host: DrawerHost,
    private val list: ListView,
    private val contents: View,
    private val color: Int,
) : Drawable() {
    var topInset = 0
        set(value) { field = value; rebuildGradients() }
    var bottomInset = 0
        set(value) { field = value; rebuildGradients() }
    private val rowBounds = Rect()
    private val listLocation = IntArray(2)
    private val hostLocation = IntArray(2)
    private val paint = Paint()
    private val topFade = ScrimFade()
    private val bottomFade = ScrimFade()
    private val feather = 24f * host.resources.displayMetrics.density
    private var topGradient: Shader? = null
    private var bottomGradient: Shader? = null
    private var drawableAlpha = 255

    override fun onBoundsChange(bounds: Rect) = rebuildGradients()

    private fun rebuildGradients() {
        val opaque = color or Color.BLACK
        val clear = color and 0x00ffffff
        topGradient = if (topInset > 0) LinearGradient(0f, 0f, 0f, topInset + feather,
            intArrayOf(opaque, opaque, clear), floatArrayOf(0f, topInset * 0.5f / (topInset + feather), 1f), Shader.TileMode.CLAMP) else null
        bottomGradient = if (bottomInset > 0) LinearGradient(0f, bounds.height().toFloat(), 0f, bounds.height() - bottomInset - feather,
            intArrayOf(opaque, opaque, clear), floatArrayOf(0f, bottomInset * 0.5f / (bottomInset + feather), 1f), Shader.TileMode.CLAMP) else null
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (host.progress <= 0f || contents.alpha <= 0f) {
            topFade.reset()
            bottomFade.reset()
            return
        }
        var topCovered = false
        var bottomCovered = false
        list.getLocationInWindow(listLocation)
        host.getLocationInWindow(hostLocation)
        for (index in 0 until list.childCount) {
            val row = list.getChildAt(index)
            row.getDrawingRect(rowBounds)
            rowBounds.offset(row.left + listLocation[0] - hostLocation[0], row.top + listLocation[1] - hostLocation[1])
            topCovered = topCovered || (rowBounds.top < topInset && rowBounds.bottom > 0)
            bottomCovered = bottomCovered || (rowBounds.bottom > bounds.height() - bottomInset && rowBounds.top < bounds.height())
        }
        val now = SystemClock.uptimeMillis()
        val topAlpha = topFade.update(topCovered && topInset > 0, now)
        val bottomAlpha = bottomFade.update(bottomCovered && bottomInset > 0, now)
        paint.shader = topGradient
        paint.alpha = (204 * contents.alpha * topAlpha * drawableAlpha / 255).toInt()
        if (topInset > 0) canvas.drawRect(0f, 0f, bounds.width().toFloat(), topInset + feather, paint)
        paint.shader = bottomGradient
        paint.alpha = (204 * contents.alpha * bottomAlpha * drawableAlpha / 255).toInt()
        if (bottomInset > 0) canvas.drawRect(0f, bounds.height() - bottomInset - feather, bounds.width().toFloat(), bounds.height().toFloat(), paint)
        if (topFade.isAnimating(now) || bottomFade.isAnimating(now)) host.postInvalidateOnAnimation()
    }

    override fun setAlpha(alpha: Int) { drawableAlpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
