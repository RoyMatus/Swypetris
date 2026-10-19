package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Native routing over free menu areas, using the real shared theme/persistence owner. */
class MenuThemeSwipeTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private var exits = 0
    private var updateChecks = 0
    private val controls = listOf("newGame", "resumeGame", "settings", "help", "results", "contacts",
        "exitGame", "versionCheck")

    @Test fun leftLogoSwipeSelectsOneNextThemeAndWraps() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.options.setPalette("synthwave_84")
        mount(model)
        swipeAt(rootHeight() * .2f, left = true)
        compose.runOnIdle { assertEquals("classic", model.paletteId) }
        swipeAt(rootHeight() * .2f, left = true)
        compose.runOnIdle { assertEquals("monokai", model.paletteId) }
    }

    @Test fun rightLogoSwipeSelectsOnePreviousThemeAndWraps() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.options.setPalette("classic")
        mount(model)
        swipeAt(rootHeight() * .2f, left = false)
        compose.runOnIdle { assertEquals("synthwave_84", model.paletteId) }
        swipeAt(rootHeight() * .2f, left = false)
        compose.runOnIdle { assertEquals("catppuccin", model.paletteId) }
    }

    @Test fun freeAreaBelowButtonsPersistsSilentlyWithoutChangingThePausedGame() {
        val feedback = Recorder()
        val state = GameState(active = Piece(Tetromino.T, y = 8), next = Tetromino.O,
            held = Tetromino.L, score = 123_456, completedRounds = 1)
        val model = GameViewModel(app, state, { 1000L }, false, feedback)
        model.pause()
        model.options.setPalette("classic")
        model.options.setRandomTheme(true)
        val before = model.game
        mount(model)
        val root = surface().fetchSemanticsNode().boundsInRoot
        val exit = compose.onNodeWithTag("exitGame").fetchSemanticsNode().boundsInRoot
        swipeAt((exit.bottom + root.bottom) / 2 - root.top, left = true)
        compose.runOnIdle {
            assertEquals("monokai", model.paletteId)
            assertFalse(model.randomThemeEnabled)
            assertSame(before, model.game)
            assertEquals(GameScreen.MENU, model.screen)
            assertEquals(0, exits)
            assertEquals(0, updateChecks)
            assertEquals(0, feedback.events)
            val restored = GameViewModel(app, null, { 1000L }, false)
            assertEquals("monokai", restored.paletteId)
            assertFalse(restored.randomThemeEnabled)
            assertEquals(before, restored.game)
        }
    }

    @Test fun tapsShortMovesAndVerticalSwipesDoNotSelectThemes() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.options.setPalette("classic")
        mount(model)
        surface().performTouchInput {
            val start = Offset(width * .6f, height * .2f)
            click(start)
            swipe(start, start - Offset(width * .02f, 0f), 100)
            swipe(start, start + Offset(0f, height * .3f), 350)
        }
        compose.runOnIdle { assertUntouched(model) }
    }

    @Test fun diagonalAndVerticalFirstDragsDoNotBecomeHorizontalSwipes() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.options.setPalette("classic")
        mount(model)
        surface().performTouchInput {
            val start = Offset(width * .75f, height * .15f)
            swipe(start, start + Offset(-width * .45f, width * .36f), 350)
            down(start)
            moveTo(start + Offset(0f, height * .25f), 200)
            moveTo(start + Offset(-width * .5f, height * .25f), 200)
            up()
        }
        compose.runOnIdle { assertUntouched(model) }
    }

    @Test fun canceledSwipeDoesNotChangeTheTheme() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.options.setPalette("classic")
        mount(model)
        surface().performTouchInput {
            down(Offset(width * .8f, height * .2f))
            moveTo(Offset(width * .2f, height * .2f), 350)
            cancel()
        }
        compose.runOnIdle { assertUntouched(model) }
    }

    @Test fun aSecondPointerCancelsAnOtherwiseHorizontalGesture() {
        val model = GameViewModel(app, null, { 1000L }, false)
        model.options.setPalette("classic")
        mount(model)
        surface().performTouchInput {
            val y = height * .2f
            down(0, Offset(width * .8f, y))
            moveTo(0, Offset(width * .6f, y), 200)
            down(1, Offset(width * .6f, y + width * .1f))
            moveTo(0, Offset(width * .2f, y), 200)
            moveTo(1, Offset(width * .2f, y + width * .1f), 200)
            up(0)
            up(1)
        }
        compose.runOnIdle { assertUntouched(model) }
    }

    @Test fun swipesStartingOnEveryControlDoNotSelectOrActivateButtons() {
        val model = pausedModel()
        val before = model.game
        mount(model)
        for (tag in controls) {
            val root = surface().fetchSemanticsNode().boundsInRoot
            val button = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val start = button.center - root.topLeft
            val end = Offset(root.width * if (start.x < root.width / 2) .99f else .01f, start.y)
            surface().performTouchInput { swipe(start, end, 350) }
            compose.runOnIdle {
                assertUntouched(model)
                assertSame(before, model.game)
            }
        }
    }

    @Test fun normalControlTapsKeepTheirExistingActions() {
        val model = pausedModel()
        mount(model)
        for ((tag, screen) in listOf("settings" to GameScreen.SETTINGS, "help" to GameScreen.HELP,
            "results" to GameScreen.RESULTS, "contacts" to GameScreen.CONTACTS)) {
            compose.onNodeWithTag(tag).performTouchInput { click() }
            compose.runOnIdle { assertEquals(screen, model.screen); model.navigation.menu() }
        }
        compose.onNodeWithTag("resumeGame").performTouchInput { click() }
        compose.runOnIdle { assertEquals(GameScreen.PLAYING, model.screen); model.pause() }
        compose.onNodeWithTag("newGame").performTouchInput { click() }
        compose.runOnIdle { assertEquals(GameScreen.PLAYING, model.screen); model.pause() }
        compose.onNodeWithTag("versionCheck").performTouchInput { click() }
        compose.onNodeWithTag("exitGame").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, updateChecks)
            assertEquals(1, exits)
            assertEquals("classic", model.paletteId)
        }
    }

    @Test fun freeSwipeEndingInsideAButtonCannotActivateIt() {
        val model = pausedModel()
        val before = model.game
        mount(model)
        val root = surface().fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("newGame").fetchSemanticsNode().boundsInRoot
        val start = Offset((button.left - root.left) / 2, button.center.y - root.top)
        val end = button.center - root.topLeft
        surface().performTouchInput { swipe(start, end, 350) }
        compose.runOnIdle {
            assertEquals("synthwave_84", model.paletteId)
            assertEquals(GameScreen.MENU, model.screen)
            assertSame(before, model.game)
            assertEquals(0, exits)
            assertEquals(0, updateChecks)
        }
    }

    @Test fun introInputCannotLeakIntoThemeSelectionOrGameActions() {
        val model = GameViewModel(app, null, { 1000L }, false, showLaunchIntro = true)
        model.options.setPalette("classic")
        mount(model)
        swipeAt(rootHeight() * .2f, left = true)
        compose.runOnIdle {
            assertUntouched(model)
            assertEquals(null, model.game)
            model.navigation.finishLaunchIntro()
        }
        swipeAt(rootHeight() * .2f, left = true)
        compose.runOnIdle { assertEquals("monokai", model.paletteId) }
    }

    @Test fun portraitSizesAndLargeFontsKeepGeometryAndTextAfterSelection() {
        val model = pausedModel()
        var viewport by mutableStateOf(Triple(320, 720, 1f))
        mount(model) { viewport }
        for ((size, theme) in listOf(Triple(240, 640, 2f) to "solarized_light",
            Triple(320, 720, 1f) to "classic", Triple(393, 873, 1.5f) to "synthwave_84")) {
            compose.runOnIdle { viewport = size; model.options.setPalette(theme) }
            val root = surface().fetchSemanticsNode().boundsInRoot
            assertTrue(root.height > root.width)
            val before = controls.associateWith {
                compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            val text = texts()
            swipeAt(rootHeight() * .2f, left = true)
            compose.runOnIdle { assertEquals(GamePalettes.adjacentId(theme, true), model.paletteId) }
            assertEquals(text, texts())
            controls.forEach { tag ->
                assertEquals(tag, before[tag], compose.onNodeWithTag(tag).assertIsDisplayed()
                    .fetchSemanticsNode().boundsInRoot)
            }
        }
    }

    private fun pausedModel(): GameViewModel = GameViewModel(app,
        GameState(active = Piece(Tetromino.O, y = 8), next = Tetromino.T, score = 1234), { 1000L }, false)
        .also { it.pause(); it.options.setPalette("classic") }

    private fun assertUntouched(model: GameViewModel) {
        assertEquals("classic", model.paletteId)
        assertEquals(GameScreen.MENU, model.screen)
        assertEquals(0, exits)
        assertEquals(0, updateChecks)
    }

    private fun texts() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
        useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
        node.config[SemanticsProperties.Text].map { it.text }
    }.sorted()

    private fun mount(model: GameViewModel, viewport: () -> Triple<Int, Int, Float> = { Triple(320, 720, 1f) }) {
        compose.setContent {
            val (width, height, scale) = viewport()
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                    val palette = GamePalettes.find(model.paletteId)
                    CompositionLocalProvider(LocalGamePalette provides palette,
                        LocalMenuSkin provides rememberMenuSkin(palette)) {
                        MainMenu(model, { exits++ }, { updateChecks++ })
                    }
                }
            }
        }
    }

    private fun surface() = compose.onNodeWithTag("mainMenuSurface")

    private fun rootHeight() = surface().fetchSemanticsNode().boundsInRoot.height

    private fun swipeAt(y: Float, left: Boolean) {
        val width = surface().fetchSemanticsNode().boundsInRoot.width
        val from = Offset(width * if (left) .8f else .2f, y)
        val to = Offset(width * if (left) .2f else .8f, y)
        surface().performTouchInput { swipe(from, to, 350) }
    }

    private class Recorder : GameFeedback {
        var events = 0
        override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) { events++ }
        override fun stop() = Unit
        override fun release() = Unit
    }
}
