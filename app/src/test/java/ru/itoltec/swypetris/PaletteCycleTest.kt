package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteCycleTest {
    @Test fun forwardFollowsExistingOrderAndWraps() {
        GamePalettes.all.zipWithNext().forEach { (current, next) ->
            assertEquals(next.id, GamePalettes.adjacentId(current.id, true))
        }
        assertEquals("classic", GamePalettes.adjacentId("synthwave_84", true))
    }

    @Test fun backwardFollowsExistingOrderAndWraps() {
        assertEquals("synthwave_84", GamePalettes.adjacentId("classic", false))
        GamePalettes.all.zipWithNext().forEach { (previous, current) ->
            assertEquals(previous.id, GamePalettes.adjacentId(current.id, false))
        }
    }

    @Test fun unknownIdUsesClassicFallbackBeforeStepping() {
        assertEquals("monokai", GamePalettes.adjacentId("retired-palette", true))
        assertEquals("synthwave_84", GamePalettes.adjacentId("retired-palette", false))
    }

    @Test fun cyclingPreservesTheAuthoredListAndRoundTrips() {
        val order = listOf("classic", "monokai", "gruvbox", "vscode", "dracula", "nord", "solarized_light",
            "solarized_dark", "github_light", "tokyo_night", "catppuccin", "synthwave_84")
        order.forEach { id ->
            assertEquals(id, GamePalettes.adjacentId(GamePalettes.adjacentId(id, true), false))
        }
        assertEquals(order, GamePalettes.all.map { it.id })
    }
}
