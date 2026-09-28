package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StatisticsResetTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun cancellationPreservesDataAndConfirmationClearsOnlyStatistics() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val preferences = GameStorage.preferences(app)
        preferences.edit().putString("palette", "github_light").putString("difficulty", Difficulty.HARD.id)
            .putBoolean("sound", false).putInt("record", 4321).putInt("record_v4", 5678).commit()
        ResultStore(preferences).write(listOf(GameResult("old", 1L, "Player", 9000, 2, 1, 1200)))
        val state = GameState(active = Piece(Tetromino.T), next = Tetromino.O, score = 123)
        val model = GameViewModel(app, state, { 1000L }, false)
        model.settings()
        compose.setContent { SwypetrisApp(model) {} }

        compose.onNodeWithTag("resetStatistics").performScrollTo().performClick()
        compose.onNodeWithTag("cancelResetStatistics").assertExists().performClick()
        compose.runOnIdle {
            assertEquals(1, model.results.size)
            assertEquals(5678, model.legacyRecord)
        }

        compose.onNodeWithTag("resetStatistics").performClick()
        compose.onNodeWithTag("confirmResetStatistics").performClick()
        compose.runOnIdle {
            assertTrue(model.results.isEmpty())
            assertEquals(0, model.legacyRecord)
            assertEquals(0, model.record)
            assertEquals(state, model.game)
            assertTrue(ResultStore(preferences).read().isEmpty())
            assertFalse(preferences.contains("record"))
            assertFalse(preferences.contains("record_v4"))
            assertEquals("github_light", preferences.getString("palette", null))
            assertEquals(Difficulty.HARD.id, preferences.getString("difficulty", null))
            assertFalse(preferences.getBoolean("sound", true))
            val restarted = GameViewModel(app, null, { 1000L }, false)
            assertTrue(restarted.results.isEmpty())
            assertEquals(0, restarted.legacyRecord)
            assertEquals(state, restarted.game)
        }
    }
}
