package com.premiumtv.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    @Test fun newAppsAppendWithoutDisturbingSavedOrder() {
        assertEquals(listOf("b", "a", "c"), AppOrder.merge(listOf("b", "a"), listOf("a", "b", "c")))
    }
    @Test fun uninstalledAndDuplicateEntriesDoNotCreateGhostTiles() {
        assertEquals(listOf("b", "a"), AppOrder.merge(listOf("gone", "b", "b"), listOf("a", "b", "a")))
    }
    @Test fun movingAnAppShiftsInterveningApps() {
        assertEquals(listOf("b", "c", "a", "d"), AppOrder.move(listOf("a", "b", "c", "d"), 0, 2))
    }
    @Test fun invalidMovePreservesOrder() {
        val original = listOf("a", "b")
        assertEquals(original, AppOrder.move(original, 0, -1))
        assertEquals(original, AppOrder.move(original, 2, 0))
    }
}
