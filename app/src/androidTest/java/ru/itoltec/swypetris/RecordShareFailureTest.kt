package ru.itoltec.swypetris

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordShareFailureTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun recoverableShareFailureCanBeDismissedWithoutLosingContinueOrWritingHistory() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException("Test receiver unavailable") }
        }
        val model = GameViewModel(app, GameState(active = Piece(Tetromino.O), next = Tetromino.T,
            score = 79_999), { 1000L }, false)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalVictoryAnimations provides false) {
                SwypetrisApp(model) {}
            }
        }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        compose.onNodeWithTag("shareRoundRecord").performClick()
        compose.waitUntil(timeoutMillis = 10000) {
            compose.onAllNodes(hasText("Не удалось поделиться", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle {
            assertEquals(GameScreen.VICTORY, model.screen)
            assertTrue(model.results.isEmpty())
        }
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithTag("nextRound").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(GameScreen.PLAYING, model.screen)
            assertEquals(80_000L, model.game!!.score)
            assertTrue(model.results.isEmpty())
        }
    }
}
