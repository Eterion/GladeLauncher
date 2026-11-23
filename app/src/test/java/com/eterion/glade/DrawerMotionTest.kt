package com.eterion.glade

import org.junit.Assert.*
import org.junit.Test

class DrawerMotionTest {
    @Test fun `a slow release settles to the nearest end`() {
        assertFalse(DrawerMotion.shouldOpen(0.3f, 0f, 1000f))
        assertTrue(DrawerMotion.shouldOpen(0.7f, 0f, 1000f))
    }

    @Test fun `an upward fling opens even after a short drag`() {
        assertTrue(DrawerMotion.shouldOpen(0.1f, -1500f, 1000f))
    }

    @Test fun `a downward fling closes even near the open end`() {
        assertFalse(DrawerMotion.shouldOpen(0.9f, 1500f, 1000f))
    }

    @Test fun `background appears first and apps wait for the release threshold`() {
        assertTrue(DrawerMotion.surfaceAlpha(0.25f) > 0f)
        assertEquals(0f, DrawerMotion.contentAlpha(0.25f), 0f)
        assertTrue(DrawerMotion.shouldOpen(DrawerMotion.OPEN_THRESHOLD, 0f, 1000f))
        assertEquals(1f, DrawerMotion.surfaceAlpha(DrawerMotion.OPEN_THRESHOLD), 0f)
        assertEquals(0f, DrawerMotion.contentAlpha(DrawerMotion.OPEN_THRESHOLD), 0f)
        assertTrue(DrawerMotion.contentAlpha(0.75f) > 0f)
        assertTrue(DrawerMotion.contentAlpha(0.75f) < 1f)
        assertEquals(1f, DrawerMotion.contentAlpha(1f), 0f)
    }

    @Test fun `reversing below the threshold hides content again`() {
        assertTrue(DrawerMotion.contentAlpha(0.8f) > 0f)
        assertEquals(0f, DrawerMotion.contentAlpha(0.3f), 0f)
        assertEquals(0f, DrawerMotion.surfaceAlpha(0f), 0f)
        assertEquals(1f, DrawerMotion.surfaceAlpha(1f), 0f)
    }
}
