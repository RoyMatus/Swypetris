package ru.itoltec.swypetris

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WidePalettePickerTest {
    @get:Rule val compose = createComposeRule()

    /** The approved landscape-free list uses large previews and one full-width card per theme. */
    @Test fun wideCardsHaveLargeSamplesAndSelectableSemantics() {
        var selected by mutableStateOf(GamePalettes.find("classic"))
        compose.setContent {
            Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                WidePalettePicker(selected) { selected = GamePalettes.find(it) }
            }
        }
        for (theme in GamePalettes.all) {
            val card = compose.onNodeWithTag("palette_${theme.id}_preview").performScrollTo().assertIsDisplayed()
            card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            val cardBounds = card.fetchSemanticsNode().boundsInRoot
            val art = compose.onNodeWithTag("palette_${theme.id}_art", true).fetchSemanticsNode().boundsInRoot
            val label = compose.onNodeWithTag("palette_${theme.id}_label", true).fetchSemanticsNode().boundsInRoot
            val density = compose.density.density
            assertEquals("Full-width card", 360f, cardBounds.width / density, 1f)
            assertEquals("Four 32dp blocks and three 4dp gaps", 140f, art.width / density, 1f)
            assertEquals("Two rows of 32dp blocks", 68f, art.height / density, 1f)
            assertTrue("Title is left of artwork", label.right <= art.left)
            card.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            compose.runOnIdle { assertEquals(theme.id, selected.id) }
            compose.onNodeWithTag("palette_${theme.id}_check", true).assertExists()
        }
    }

    /** Long labels stay complete on narrow portrait layouts and at doubled system font scale. */
    @Test fun narrowAndLargeFontCardsHaveUnclippedNamesAndArtwork() {
        var width by mutableStateOf(240.dp)
        var font by mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, font)) {
                Column(Modifier.width(width).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    WidePalettePicker(GamePalettes.find("classic")) {}
                }
            }
        }
        for ((requestedWidth, scale) in listOf(240.dp to 1f, 240.dp to 2f, 360.dp to 2f)) {
            compose.runOnIdle { width = requestedWidth; font = scale }
            for (theme in GamePalettes.all) {
                val card = compose.onNodeWithTag("palette_${theme.id}_preview").performScrollTo().assertIsDisplayed()
                val bounds = card.fetchSemanticsNode().boundsInRoot
                val label = compose.onNodeWithTag("palette_${theme.id}_label", true).assertTextEquals(theme.title)
                    .fetchSemanticsNode()
                val art = compose.onNodeWithTag("palette_${theme.id}_art", true).fetchSemanticsNode().boundsInRoot
                assertTrue("Title stays above artwork", label.boundsInRoot.bottom <= art.top)
                assertTrue("Artwork stays inside card",
                    art.left >= bounds.left && art.right <= bounds.right && art.bottom <= bounds.bottom)
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
                assertFalse("Full label at $requestedWidth/$scale", layouts.single().hasVisualOverflow)
            }
        }
    }
}
