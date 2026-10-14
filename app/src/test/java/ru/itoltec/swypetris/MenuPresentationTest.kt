package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuPresentationTest {
    @Test fun skinIsRestrictedToStandaloneMenusAndNeverCropPreview() {
        val allowed = setOf(GameScreen.MENU, GameScreen.SETTINGS, GameScreen.HELP,
            GameScreen.CONTACTS, GameScreen.PRIVACY, GameScreen.LEGAL, GameScreen.RESULTS)
        GameScreen.entries.forEach { screen ->
            assertEquals(screen.name, screen in allowed, usesMenuSkin(screen, false))
            assertEquals("crop ${screen.name}", false, usesMenuSkin(screen, true))
        }
    }

    @Test fun disabledPressedFocusedSelectedPriorityMatchesSuppliedSelectors() {
        assertEquals(MenuControlState.DISABLED, menuControlState(false, true, true, true))
        assertEquals(MenuControlState.PRESSED, menuControlState(true, true, true, true))
        assertEquals(MenuControlState.FOCUSED, menuControlState(true, false, true, true))
        assertEquals(MenuControlState.SELECTED, menuControlState(true, false, false, true))
        assertEquals(MenuControlState.NORMAL, menuControlState(true, false, false, false))
    }
}
