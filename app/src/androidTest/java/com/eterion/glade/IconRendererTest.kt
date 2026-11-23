package com.eterion.glade

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class IconRendererTest {
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test fun adaptiveIconUsesItsLayersAndSquircleInsteadOfSystemCircle() {
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.TRANSPARENT))
        val rendered = IconRenderer(resources, 100).render(icon).bitmap
        assertEquals(0, Color.alpha(rendered.getPixel(0, 0)))
        assertEquals(Color.RED, rendered.getPixel(50, 50))
        // This shoulder is inside the squircle and outside the corresponding circle.
        assertEquals(Color.RED, rendered.getPixel(88, 88))
        assertEquals(0, Color.alpha(rendered.getPixel(91, 91)))
        assertEquals(0, Color.alpha(rendered.getPixel(98, 98)))
    }

    @Test fun legacyCircleGetsSquircleBackingWhileKeepingArtwork() {
        val original = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(original).drawCircle(50f, 50f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLUE })
        val rendered = IconRenderer(resources, 100).render(BitmapDrawable(resources, original)).bitmap
        assertEquals(Color.BLUE, rendered.getPixel(50, 50))
        assertEquals(Color.WHITE, rendered.getPixel(88, 88))
        assertEquals(0, Color.alpha(rendered.getPixel(0, 0)))
    }
}
