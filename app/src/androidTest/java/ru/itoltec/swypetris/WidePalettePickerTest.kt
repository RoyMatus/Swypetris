package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Acceptance checks for the approved full-width theme cards and their real settings owner. */
class WidePalettePickerTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun cardsShowLargeMaterialSamplesAndPersistRadioSelection() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.options.setRandomTheme(true)
        setPicker(model, 360, 800, 1f)

        compose.onAllNodes(hasTestTag("paletteGrid"), useUnmergedTree = true).assertCountEquals(1)
        GamePalettes.all.forEach { theme ->
            val card = compose.onNodeWithTag("palette_${theme.id}_preview")
                .performScrollTo().assertIsDisplayed()
            card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            val cardBounds = card.fetchSemanticsNode().boundsInRoot
            val art = compose.onNodeWithTag("palette_${theme.id}_art", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val label = compose.onNodeWithTag("palette_${theme.id}_label", useUnmergedTree = true)
                .assertTextEquals(theme.title).fetchSemanticsNode().boundsInRoot

            val density = effectiveDensity(360)
            assertEquals("One full-width card per theme", 360f, cardBounds.width / density, 1f)
            assertEquals("Four 32dp blocks with three 4dp gaps", 140f, art.width / density, 1f)
            assertEquals("Two rows of 32dp blocks with a 4dp gap", 68f, art.height / density, 1f)
            assertTrue("Title is left of artwork for ${theme.id}", label.right <= art.left)
            compose.runOnIdle { model.options.setRandomTheme(true) }
            card.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            compose.runOnIdle {
                assertEquals(theme.id, model.paletteId)
                assertFalse("Choosing a theme disables random theme changes", model.randomThemeEnabled)
            }
            compose.onNodeWithTag("palette_${theme.id}_check", useUnmergedTree = true).assertExists()
            compose.runOnIdle {
                assertEquals("Theme selection persists", theme.id,
                    GameViewModel(app, null, { 1000L }, false).paletteId)
            }
        }
        val displayedCardIds = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag),
            useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrElse(SemanticsProperties.TestTag) { "" }
                .takeIf { it.startsWith("palette_") && it.endsWith("_preview") }
                ?.removePrefix("palette_")?.removeSuffix("_preview")
        }
        assertEquals("Each theme appears once in GamePalettes.all order",
            GamePalettes.all.map { it.id }, displayedCardIds)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
    }

    @Test fun namesAndSamplesFitPortraitWidthsAndLargeFontScales() {
        val model = GameViewModel(app, null, { 1000L }, false)
        var viewport by mutableStateOf(DpSize(240.dp, 800.dp))
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport)) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalGamePalette provides GamePalettes.find(model.paletteId)) {
                    Box(Modifier.requiredSize(viewport.width, viewport.height).testTag("widePickerViewport")) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            PalettePicker(model)
                        }
                    }
                }
            }
        }

        for ((width, scale) in listOf(240 to 1f, 320 to 1f, 393 to 1f, 600 to 1f,
            240 to 1.4f, 320 to 1.4f, 393 to 1.4f, 600 to 1.4f,
            240 to 2f, 320 to 2f, 393 to 2f, 600 to 2f)) {
            compose.runOnIdle { viewport = DpSize(width.dp, 800.dp); fontScale = scale }
            compose.waitForIdle()
            val stacked = width < 294 || scale >= 1.4f
            for (theme in GamePalettes.all) {
                val card = compose.onNodeWithTag("palette_${theme.id}_preview")
                    .performScrollTo().assertIsDisplayed()
                val cardBounds = card.fetchSemanticsNode().boundsInRoot
                val label = compose.onNodeWithTag("palette_${theme.id}_label", useUnmergedTree = true)
                    .assertTextEquals(theme.title).fetchSemanticsNode()
                val art = compose.onNodeWithTag("palette_${theme.id}_art", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val labelBounds = label.boundsInRoot
                if (stacked) {
                    assertTrue("${theme.id} title stays above artwork at $width/$scale",
                        labelBounds.bottom <= art.top)
                    val blockDp = art.width / effectiveDensity(width) / 7f - 24f / 7f
                    assertTrue("Seven samples stay visible at $width/$scale", art.width > 0f && blockDp > 0f)
                } else {
                    assertEquals("Normal preview width at $width/$scale", 140f,
                        art.width / effectiveDensity(width), 1f)
                    assertEquals("Normal preview height at $width/$scale", 68f,
                        art.height / effectiveDensity(width), 1f)
                    assertTrue("${theme.id} title stays left of artwork at $width/$scale",
                        labelBounds.right <= art.left)
                }
                assertTrue("Artwork stays inside ${theme.id} card at $width/$scale",
                    art.left >= cardBounds.left && art.right <= cardBounds.right && art.bottom <= cardBounds.bottom)
                assertTrue("Title stays inside ${theme.id} card at $width/$scale",
                    labelBounds.left >= cardBounds.left && labelBounds.right <= cardBounds.right &&
                        labelBounds.top >= cardBounds.top && labelBounds.bottom <= cardBounds.bottom)
                assertTextUsesAvailableNodeWidth(label)
            }
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
    }

    private fun setPicker(model: GameViewModel, width: Int, height: Int, fontScale: Float) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalGamePalette provides GamePalettes.find(model.paletteId)) {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("widePickerViewport")) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            PalettePicker(model)
                        }
                    }
                }
            }
        }
    }

    /** ForcedSize can scale density to fit its requested portrait height on the host display. */
    private fun effectiveDensity(widthDp: Int): Float =
        compose.onNodeWithTag("widePickerViewport").fetchSemanticsNode().boundsInRoot.width / widthDp

    /** Compose 1.7 text semantics can report a paragraph measured at the parent's wider constraint. */
    private fun assertTextUsesAvailableNodeWidth(node: androidx.compose.ui.semantics.SemanticsNode) {
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        val input = layouts.single().layoutInput
        lateinit var measured: TextLayoutResult
        compose.runOnIdle {
            measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection,
                cacheSize = 0).measure(input.text, style = input.style, overflow = input.overflow,
                softWrap = input.softWrap, maxLines = input.maxLines, placeholders = input.placeholders,
                constraints = Constraints(maxWidth = node.size.width, maxHeight = node.size.height), skipCache = true)
        }
        val rightmostLine = (0 until measured.lineCount).maxOfOrNull(measured::getLineRight) ?: 0f
        val lastCharacterRight = if (input.text.isNotEmpty())
            measured.getBoundingBox(input.text.lastIndex).right else 0f
        val exceededMaxLines = measured.multiParagraph.didExceedMaxLines
        val ellipsized = (0 until measured.lineCount).any(measured::isLineEllipsized)
        assertFalse("${input.text}: complete label must fit its actual semantics node; " +
            "node=${node.size}, measured=${measured.size}, rightmostLine=$rightmostLine, " +
            "lastCharacterRight=$lastCharacterRight, visualOverflow=${measured.hasVisualOverflow}, " +
            "exceededMaxLines=$exceededMaxLines, ellipsized=$ellipsized",
            measured.hasVisualOverflow || exceededMaxLines || ellipsized)
        assertTrue("${input.text}: line exceeds actual node width", rightmostLine <= node.size.width)
        assertTrue("${input.text}: final character exceeds actual node width", lastCharacterRight <= node.size.width)
        assertTrue("${input.text}: text exceeds actual node height", measured.size.height <= node.size.height)
    }
}
