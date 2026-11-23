package com.eterion.glade

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LayoutStoreTest {
    private val preferences = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("layout_test", 0)
    private val store = LayoutStore(preferences)

    @Before fun reset() { preferences.edit().clear().commit() }
    @After fun cleanup() { preferences.edit().clear().commit() }

    @Test fun placementRoundTripAndRemoval() {
        val items = listOf(Placement("example.one/.Main", 0), Placement("example.two/.Main", 29))
        store.write(items)
        assertEquals(items, LayoutStore(preferences).read())
        store.write(items.drop(1))
        assertEquals(items.drop(1), LayoutStore(preferences).read())
    }

    @Test fun malformedStorageFallsBackToEmpty() {
        preferences.edit().putString("home_v1", "broken JSON").commit()
        assertTrue(store.read().isEmpty())
    }

    @Test fun invalidAndDuplicateEntriesDoNotOverwriteGoodPlacement() {
        preferences.edit().putString("home_v1", """[
            {"component":"example.one/.Main","cell":25},
            {"component":"example.two/.Main","cell":25},
            {"component":"example.one/.Main","cell":0},
            {"component":"example.bad/.Main","cell":99}
        ]""").putInt("home_rows", HomeLayout.ROWS).commit()
        assertEquals(listOf(Placement("example.one/.Main", 25)), store.read())
    }

    @Test fun previousSixRowLayoutMigratesOnceWithoutLosingShortcuts() {
        preferences.edit().putString("home_v1", """[
            {"component":"example.top/.Main","cell":0},
            {"component":"example.bottom/.Main","cell":29}
        ]""").commit()
        val expected = listOf(Placement("example.top/.Main", 0), Placement("example.bottom/.Main", 34))
        assertEquals(expected, store.read())
        assertEquals(7, preferences.getInt("home_rows", 0))
        assertEquals(expected, LayoutStore(preferences).read())
    }

    @Test fun quickAccessRoundTripsIndependentlyOfHomeLayout() {
        val homeItems = listOf(Placement("example.home/.Main", 34))
        store.write(homeItems)
        store.writeQuickAccess(listOf("phone/.Main", "camera/.Main", "phone/.Main"))
        assertEquals(listOf("phone/.Main", "camera/.Main"), LayoutStore(preferences).readQuickAccess())
        store.writeQuickAccess(listOf("camera/.Main"))
        assertEquals(homeItems, store.read())
        assertEquals(listOf("camera/.Main"), store.readQuickAccess())
    }

    @Test fun malformedQuickAccessDoesNotBreakHomeLayout() {
        store.write(listOf(Placement("example.home/.Main", 34)))
        preferences.edit().putString("quick_access_v1", "broken JSON").commit()
        assertTrue(store.readQuickAccess().isEmpty())
        assertEquals(1, store.read().size)
    }
}
