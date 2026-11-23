package com.eterion.glade

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.pow

/** Rasterizes once on the catalog worker, not while binding or scrolling icon views. */
class IconRenderer(private val resources: Resources, private val size: Int) {
    private fun newBitmap() = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        density = resources.displayMetrics.densityDpi
    }

    private val mask = newBitmap().also {
        val path = Path()
        val radius = size / 2f
        // A 3.2-power superellipse keeps squircle shoulders with a rounder silhouette.
        for (step in 0 until 128) {
            val angle = step * 2.0 * Math.PI / 128
            val x = cos(angle)
            val y = kotlin.math.sin(angle)
            val px = radius + radius * sign(x).toFloat() * abs(x).pow(2.0 / 3.2).toFloat()
            val py = radius + radius * sign(y).toFloat() * abs(y).pow(2.0 / 3.2).toFloat()
            if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        Canvas(it).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun render(icon: Drawable): BitmapDrawable {
        val bitmap = newBitmap()
        val canvas = Canvas(bitmap)
        if (icon is AdaptiveIconDrawable) {
            // Render the layers directly, avoiding the device's existing circular icon mask.
            val extra = (size * AdaptiveIconDrawable.getExtraInsetFraction()).toInt()
            for (layer in listOfNotNull(icon.background, icon.foreground)) {
                layer.setBounds(-extra, -extra, size + extra, size + extra)
                layer.draw(canvas)
            }
        } else {
            // Older transparent/circular artwork gets the same outer shape and breathing room.
            canvas.drawColor(Color.WHITE)
            val inset = (size * 0.08f).toInt()
            val available = size - 2 * inset
            val width = icon.intrinsicWidth.takeIf { it > 0 } ?: available
            val height = icon.intrinsicHeight.takeIf { it > 0 } ?: available
            val scale = available.toFloat() / maxOf(width, height)
            val drawWidth = (width * scale).toInt()
            val drawHeight = (height * scale).toInt()
            val left = (size - drawWidth) / 2
            val top = (size - drawHeight) / 2
            icon.setBounds(left, top, left + drawWidth, top + drawHeight)
            icon.draw(canvas)
        }
        // A full bitmap mask clears pixels outside the path and antialiases its boundary.
        canvas.drawBitmap(mask, 0f, 0f, maskPaint)
        return BitmapDrawable(resources, bitmap)
    }
}
