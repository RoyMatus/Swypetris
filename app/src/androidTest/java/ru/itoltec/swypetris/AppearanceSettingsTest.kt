package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings entry-point coverage for appearance switch semantics and portrait text fit. */
class AppearanceSettingsTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun appearanceRowsToggleOnceAndPersistThroughTheirSwitchSemantics() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.navigation.settings()
        showSettings(model, DpSize(360.dp, 800.dp), 1f)

        val randomRow = compose.onNodeWithTag("randomTheme", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        val randomSwitch = compose.onNodeWithTag("randomThemeBox", useUnmergedTree = true)
        randomRow.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff()
        randomRow.performClick().assertIsOn()
        compose.runOnIdle { assertTrue(model.randomThemeEnabled) }
        val randomRestoredOn = GameViewModel(app, null, { 1000L }, false)
        compose.waitUntil(10000) { !randomRestoredOn.background.busy }
        compose.runOnIdle { assertTrue(randomRestoredOn.randomThemeEnabled) }
        randomSwitch.performTouchInput { click(Offset(center.x, center.y)) }
        randomRow.assertIsOff()
        compose.runOnIdle { assertFalse(model.randomThemeEnabled) }
        val randomRestoredOff = GameViewModel(app, null, { 1000L }, false)
        compose.waitUntil(10000) { !randomRestoredOff.background.busy }
        compose.runOnIdle { assertFalse(randomRestoredOff.randomThemeEnabled) }

        val backgroundRow = compose.onNodeWithTag("customBackgroundEnabled", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        val backgroundSwitch = compose.onNodeWithTag("customBackgroundSwitch", useUnmergedTree = true)
        backgroundRow.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff()
        backgroundRow.performClick().assertIsOn()
        compose.runOnIdle { assertTrue(model.background.enabled) }
        val backgroundRestoredOn = GameViewModel(app, null, { 1000L }, false)
        compose.waitUntil(10000) { !backgroundRestoredOn.background.busy }
        compose.runOnIdle { assertTrue(backgroundRestoredOn.background.enabled) }
        backgroundSwitch.performTouchInput { click(Offset(center.x, center.y)) }
        backgroundRow.assertIsOff()
        compose.runOnIdle { assertFalse(model.background.enabled) }
        val backgroundRestoredOff = GameViewModel(app, null, { 1000L }, false)
        compose.waitUntil(10000) { !backgroundRestoredOff.background.busy }
        compose.runOnIdle { assertFalse(backgroundRestoredOff.background.enabled) }
    }

    @Test fun appearanceRowsAndBackgroundButtonFitNarrowPortraitAtLargeFonts() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.navigation.settings()
        var viewport by mutableStateOf(DpSize(240.dp, 800.dp))
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(fontScale)) { SwypetrisApp(model) {} }
        }
        compose.waitUntil(10000) { !model.background.busy }

        for ((width, scale) in listOf(240 to 1f, 320 to 1.4f, 393 to 2f)) {
            compose.runOnIdle { viewport = DpSize(width.dp, 800.dp); fontScale = scale }
            compose.waitForIdle()
            compose.onNodeWithTag("randomTheme", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            val randomLabelNode = compose.onNodeWithText("Менять тему каждый раунд", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode()
            assertTextFitsAvailableNodeWidth(randomLabelNode, expectStartAlignment = true)
            val randomLabel = randomLabelNode.boundsInRoot
            val randomControl = compose.onNodeWithTag("randomThemeBox", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Random-theme label stays left of its switch at $width/$scale",
                randomLabel.right <= randomControl.left)

            compose.onNodeWithTag("customBackgroundEnabled", useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
            val backgroundLabelNode = compose.onNodeWithText("Использовать свой фон", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode()
            assertTextFitsAvailableNodeWidth(backgroundLabelNode, expectStartAlignment = true)
            val backgroundLabel = backgroundLabelNode.boundsInRoot
            val backgroundControl = compose.onNodeWithTag("customBackgroundSwitch", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Background label stays left of its switch at $width/$scale",
                backgroundLabel.right <= backgroundControl.left)

            val button = compose.onNodeWithTag("chooseBackground").performScrollTo().assertIsDisplayed()
            val buttonBounds = button.fetchSemanticsNode().boundsInRoot
            val buttonText = compose.onNodeWithText("Выбрать файл (до 16 Мб)", useUnmergedTree = true)
                .assertTextEquals("Выбрать файл (до 16 Мб)").assertIsDisplayed().fetchSemanticsNode()
            assertTrue("Full background action text stays inside the button at $width/$scale",
                buttonText.boundsInRoot.left >= buttonBounds.left &&
                    buttonText.boundsInRoot.right <= buttonBounds.right &&
                    buttonText.boundsInRoot.top >= buttonBounds.top &&
                    buttonText.boundsInRoot.bottom <= buttonBounds.bottom)
            assertTextFitsAvailableNodeWidth(buttonText)
            compose.onNodeWithText("Изображение до 16 МиБ. Оригинал не изменяется.")
                .assertDoesNotExist()
        }
    }

    private fun showSettings(model: GameViewModel, size: DpSize, scale: Float) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then
                DeviceConfigurationOverride.FontScale(scale)) { SwypetrisApp(model) {} }
        }
        compose.waitUntil(10000) { !model.background.busy }
    }

    private fun assertTextFitsAvailableNodeWidth(node: androidx.compose.ui.semantics.SemanticsNode,
        expectStartAlignment: Boolean = false) {
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
        assertFalse("${input.text}: text is not ellipsized",
            (0 until measured.lineCount).any(measured::isLineEllipsized))
        assertFalse("${input.text}: text fits the available width", measured.multiParagraph.didExceedMaxLines)
        assertFalse("${input.text}: text has no visual overflow", measured.hasVisualOverflow)
        if (expectStartAlignment) {
            assertTrue("${input.text}: first line starts at the label's left edge",
                measured.getLineLeft(0) <= 1f)
        }
    }
}
