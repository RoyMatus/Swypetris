package ru.itoltec.swypetris

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Checks approved attribution and the real About actions without opening another app. */
class ContactsAboutTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun approvedCopyAndRepositoryFitPortraitAndPreserveLegalNavigation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, GameState(active = Piece(Tetromino.T), next = Tetromino.O,
            score = 1234), { 1000L }, false)
        val game = model.game
        val history = model.results
        val intents = mutableListOf<Intent>()
        var viewport by mutableStateOf(DpSize(393.dp, 873.dp))
        var scale by mutableStateOf(1f)
        model.navigation.contacts()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(scale)) {
                val context = LocalContext.current
                val recording = remember(context) { RecordingContext(context, intents) }
                CompositionLocalProvider(LocalContext provides recording) { SwypetrisApp(model) {} }
            }
        }
        listOf(Triple(240, 720, 2f), Triple(320, 720, 1.5f), Triple(393, 873, 1f),
            Triple(600, 960, 2f)).forEach { (width, height, font) ->
            compose.runOnIdle { viewport = DpSize(width.dp, height.dp); scale = font }
            compose.onNodeWithTag("contactsPage", useUnmergedTree = true)
                .performScrollToNode(hasText(ABOUT_COPY))
            assertTextFits(ABOUT_COPY)
            compose.onNodeWithText(REPOSITORY, useUnmergedTree = true).performScrollTo()
            assertTextFits(REPOSITORY)
            compose.onNodeWithText(REPOSITORY, useUnmergedTree = true).performClick()
            compose.runOnIdle {
                assertEquals(Intent.ACTION_VIEW, intents.last().action)
                assertEquals(REPOSITORY, intents.last().dataString)
                assertEquals(GameScreen.CONTACTS, model.screen)
                assertEquals(game, model.game)
                assertEquals(history, model.results)
            }
        }
        compose.onNodeWithTag("contactsPage", useUnmergedTree = true).performScrollToNode(hasTestTag("legal"))
        compose.onNodeWithTag("legal", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("legalPage", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { model.navigation.contacts() }
        compose.onNodeWithTag("contactsPage", useUnmergedTree = true).performScrollToNode(hasTestTag("privacy"))
        compose.onNodeWithTag("privacy", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("privacyPage", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(game, model.game); assertEquals(history, model.results) }
    }

    private fun assertTextFits(text: String) {
        val target = compose.onNodeWithText(text, useUnmergedTree = true)
        target.performScrollTo().assertIsDisplayed()
        val node = target.fetchSemanticsNode()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Text extends beyond the left edge", node.boundsInRoot.left >= root.left)
        assertTrue("Text extends beyond the right edge", node.boundsInRoot.right <= root.right)
        val layouts = mutableListOf<TextLayoutResult>()
        lateinit var measured: TextLayoutResult
        compose.runOnIdle {
            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            val input = layouts.single().layoutInput
            measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0)
                .measure(input.text, style = input.style, overflow = input.overflow, softWrap = input.softWrap,
                    maxLines = input.maxLines, placeholders = input.placeholders,
                    constraints = Constraints(maxWidth = node.size.width, maxHeight = node.size.height),
                    skipCache = true)
        }
        assertFalse("Attribution or URL is clipped", measured.hasVisualOverflow)
        assertFalse("Attribution or URL is ellipsized", (0 until measured.lineCount).any(measured::isLineEllipsized))
    }

    private class RecordingContext(base: Context, private val intents: MutableList<Intent>) : ContextWrapper(base) {
        override fun startActivity(intent: Intent) { intents += intent }
    }

    private companion object {
        const val ABOUT_COPY = "SWYPETRIS\n\n© Первачук Александр Юрьевич aka Roy Matus\n" +
            "Создано с использованием ChatGPT"
        const val REPOSITORY = "https://github.com/RoyMatus/Swypetris"
    }
}
