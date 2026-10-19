package ru.itoltec.swypetris

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Record eligibility belongs to the run, never to writes made during celebration. */
class RoundRecordSharingTest {
    @get:Rule val storage = IsolatedStorageRule()

    @Test fun tiesAndLowerRoundsCannotShareButEveryLaterQualifyingRoundCan() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            GameStorage.migrate(GameStorage.preferences(app))
            ResultStore(GameStorage.preferences(app)).write(listOf(GameResult("prior", 100,
                "Roy", 80_000, 0, 1, 20)))
            val model = GameViewModel(app, GameState(active = Piece(Tetromino.O), next = Tetromino.T,
                score = 79_999), { 1000L }, false)
            model.input.command(GameCommand.SOFT_DROP)
            assertFalse(model.canShareRoundRecord)
            model.nextRound()
            for (score in listOf(160_000L, 240_000L)) {
                val before = model.game!!
                model.simulation.acceptState(before, before.copy(score = score, victoryPending = true), 1000L)
                assertTrue(model.canShareRoundRecord)
                assertEquals(1, model.results.size)
                val restored = GameViewModel(app, null, { 1000L }, false)
                restored.resume()
                assertTrue(restored.canShareRoundRecord)
                model.nextRound()
            }
        }
    }

    @Test fun qualifyingRoundSurvivesExternalSharingLifecycleAndRestoration() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = GameViewModel(ApplicationProvider.getApplicationContext(),
                GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79_999), { 1000L }, false)
            assertFalse(model.canShareRoundRecord)
            model.input.command(GameCommand.SOFT_DROP)
            assertTrue(model.canShareRoundRecord)
            val won = model.game
            model.onWindowFocusChanged(false)
            model.onBackground()
            model.onForeground()
            model.onWindowFocusChanged(true)
            assertEquals(GameScreen.VICTORY, model.screen)
            assertEquals(won, model.game)
            assertTrue(model.results.isEmpty())
            val restored = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false)
            restored.resume()
            assertEquals(GameScreen.VICTORY, restored.screen)
            assertTrue(restored.canShareRoundRecord)
            restored.nextRound()
            assertFalse(restored.canShareRoundRecord)
            assertEquals(80_000L, restored.game!!.score)
        }
    }
}
