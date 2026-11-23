package com.eterion.glade

import org.junit.Assert.*
import org.junit.Test

class ScrimFadeTest {
    @Test fun fadesInAndOutInsteadOfSwitchingInstantly() {
        val fade = ScrimFade()
        assertEquals(0f, fade.update(true, 1000), 0f)
        assertEquals(0.5f, fade.update(true, 1090), 0.001f)
        assertEquals(1f, fade.update(true, 1180), 0f)
        assertFalse(fade.isAnimating(1180))
        assertEquals(1f, fade.update(false, 1200), 0f)
        assertEquals(0.5f, fade.update(false, 1290), 0.001f)
        assertEquals(0f, fade.update(false, 1380), 0f)
    }

    @Test fun reversingKeepsCurrentOpacityAndHomeResetClearsIt() {
        val fade = ScrimFade()
        fade.update(true, 1000)
        val halfway = fade.update(true, 1090)
        assertEquals(halfway, fade.update(false, 1090), 0f)
        assertTrue(fade.update(false, 1135) in 0f..halfway)
        fade.reset()
        assertEquals(0f, fade.update(false, 1140), 0f)
        assertFalse(fade.isAnimating(1140))
    }
}
