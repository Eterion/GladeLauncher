package com.eterion.glade

import org.junit.Assert.*
import org.junit.Test

class QuickAccessTest {
    @Test fun `adding is ordered and idempotent while removal restores membership`() {
        val pins = QuickAccess.add(QuickAccess.add(emptyList(), "phone/.Main"), "camera/.Main")
        assertEquals(listOf("phone/.Main", "camera/.Main"), QuickAccess.add(pins, "phone/.Main"))
        assertEquals(listOf("camera/.Main"), QuickAccess.remove(pins, "phone/.Main"))
        assertEquals(pins, QuickAccess.add(pins, ""))
    }

    @Test fun `quick access moves apps out of the remaining alphabetical list`() {
        val groups = QuickAccess.group(listOf("camera", "messages", "phone"), listOf("phone", "camera"))
        assertEquals(listOf("phone", "camera"), groups.quick)
        assertEquals(listOf("messages"), groups.alphabetical)
        val removed = QuickAccess.group(listOf("camera", "messages", "phone"), listOf("camera"))
        assertEquals(listOf("messages", "phone"), removed.alphabetical)
    }

    @Test fun `temporarily missing apps are hidden without changing stored choices`() {
        val pins = listOf("missing", "phone", "phone")
        assertEquals(listOf("phone"), QuickAccess.group(listOf("camera", "phone"), pins).quick)
        assertEquals(listOf("missing", "phone", "phone"), pins)
        assertEquals(listOf("missing", "phone"), QuickAccess.group(listOf("camera", "missing", "phone"), pins).quick)
    }

    @Test fun `uninstall removes the exact package without touching similarly named apps`() {
        assertEquals(listOf("phone.extra/.Main"), QuickAccess.removePackage(listOf("phone/.Main", "phone/.Other", "phone.extra/.Main"), "phone"))
    }
}
