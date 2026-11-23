package com.eterion.glade

import org.junit.Assert.*
import org.junit.Test

class LauncherModelTest {
    @Test fun `invalid entries do not consume a cell or component`() {
        val entries = listOf(Placement("a", -1), Placement("", 1), Placement("a", 1), Placement("b", 2))
        assertEquals(listOf(Placement("a", 1), Placement("b", 2)), HomeLayout.normalize(entries))
    }

    @Test fun `duplicate components and occupied cells are rejected`() {
        val entries = listOf(Placement("a", 1), Placement("a", 2), Placement("b", 1), Placement("b", 3))
        assertEquals(listOf(Placement("a", 1), Placement("b", 3)), HomeLayout.normalize(entries))
    }

    @Test fun `shortcuts start at bottom but can move to the top`() {
        val added = HomeLayout.add(emptyList(), "a")
        assertEquals(HomeLayout.SIZE - HomeLayout.COLUMNS, added.single().cell)
        assertEquals(0, HomeLayout.move(added, "a", 0).single().cell)
    }

    @Test fun `moving onto another shortcut preserves both positions`() {
        val entries = listOf(Placement("a", 0), Placement("b", 1))
        assertEquals(entries, HomeLayout.move(entries, "a", 1))
        assertEquals(entries, HomeLayout.move(entries, "a", -1))
        assertEquals(entries, HomeLayout.move(entries, "unknown", 3))
    }

    @Test fun `full homes and repeated adds never lose existing shortcuts`() {
        val full = (0 until HomeLayout.SIZE).map { Placement("app$it", it) }
        assertEquals(full, HomeLayout.add(full, "extra"))
        assertEquals(full, HomeLayout.add(full, "app0"))
    }

    @Test fun `six row migration preserves columns and anchors top and bottom`() {
        val original = listOf(Placement("top", 2), Placement("middle", 16), Placement("bottom", 29))
        assertEquals(listOf(Placement("top", 2), Placement("middle", 21), Placement("bottom", 34)), HomeLayout.remapRows(original, 6))
        assertEquals(HomeLayout.ROWS * HomeLayout.COLUMNS, HomeLayout.SIZE)
    }

    @Test fun `uninstall frees only cells belonging to the removed package`() {
        val other = Placement("example.app.extra/.Main", 7)
        val entries = listOf(Placement("example.app/.Main", 0), other, Placement("example.app/.Second", 9))
        assertEquals(listOf(other), HomeLayout.removePackage(entries, "example.app"))
        assertEquals(entries, HomeLayout.removePackage(entries, "unrelated"))
    }
}
