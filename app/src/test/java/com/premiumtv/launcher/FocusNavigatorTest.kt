package com.premiumtv.launcher

import org.junit.Assert.*
import org.junit.Test

class FocusNavigatorTest {
    // Header, partial favorites row, header, full apps row, partial apps row.
    private val navigator = FocusNavigator(listOf(false, true, true, false, true, true, true, true, true), 3)

    @Test fun headersAreSkippedAndColumnIsPreserved() {
        assertEquals(5, navigator.next(2, FocusNavigator.Direction.DOWN, false))
        assertEquals(2, navigator.next(5, FocusNavigator.Direction.UP, false))
        assertEquals(8, navigator.next(5, FocusNavigator.Direction.DOWN, false))
    }
    @Test fun incompleteRowsChooseTheNearestExistingColumn() {
        assertEquals(8, navigator.next(6, FocusNavigator.Direction.DOWN, false))
        assertEquals(2, navigator.next(6, FocusNavigator.Direction.UP, false))
    }
    @Test fun horizontalNavigationDoesNotWrap() {
        assertEquals(2, navigator.next(2, FocusNavigator.Direction.RIGHT, false))
        assertEquals(4, navigator.next(4, FocusNavigator.Direction.LEFT, false))
        assertEquals(7, navigator.next(7, FocusNavigator.Direction.LEFT, false))
    }
    @Test fun physicalArrowsAreMirroredInArabic() {
        assertEquals(5, navigator.next(4, FocusNavigator.Direction.LEFT, true))
        assertEquals(4, navigator.next(5, FocusNavigator.Direction.RIGHT, true))
    }
    @Test fun topReturnsToolbarAndBottomKeepsFocus() {
        assertEquals(-1, navigator.next(1, FocusNavigator.Direction.UP, false))
        assertEquals(8, navigator.next(8, FocusNavigator.Direction.DOWN, false))
    }
    @Test fun emptyScreenHasNoAppFocus() {
        assertEquals(-1, FocusNavigator(listOf(false, false), 5).next(0, FocusNavigator.Direction.DOWN, false))
    }
}
