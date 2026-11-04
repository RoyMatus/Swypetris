package ru.itoltec.swypetris

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Menu artwork must have the whole Compose window available, including system-bar regions. */
@RunWith(AndroidJUnit4::class)
class MenuEdgeToEdgeTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun menuSurfaceCoversTheWindow() {
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("mainMenuSurface").fetchSemanticsNode().boundsInRoot
        assertEquals("Menu left edge", window.left, menu.left, 1f)
        assertEquals("Menu top edge", window.top, menu.top, 1f)
        assertEquals("Menu right edge", window.right, menu.right, 1f)
        assertEquals("Menu bottom edge", window.bottom, menu.bottom, 1f)
    }
}
