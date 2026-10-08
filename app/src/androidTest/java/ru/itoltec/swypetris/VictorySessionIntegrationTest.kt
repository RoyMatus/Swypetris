package ru.itoltec.swypetris

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies that the Victory transition retains the current session without a Compose host. */
class VictorySessionIntegrationTest {
    @get:Rule val storage = IsolatedStorageRule()

    private class Music : MusicPlayback {
        val modes = mutableListOf<MusicMode>()
        override fun setPlaying(enabled: Boolean) = setMode(if (enabled) MusicMode.GAME else MusicMode.SILENT)
        override fun setMode(next: MusicMode) { modes += next }
        override fun release() = Unit
    }

    /** Celebration time is excluded and a continued round keeps score, rules, and one record. */
    @Test fun victoryAndContinuationPreserveSession() {
        var now = 1000L
        val music = Music()
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { now }, false, musicPlayback = music)
        val initialGravity = model.game!!.gravityMillis

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            now += 100
            model.input.command(GameCommand.SOFT_DROP)
            assertEquals(GameScreen.VICTORY, model.screen)
            assertEquals(80000, model.game!!.score)
            assertEquals(1, music.modes.count { it == MusicMode.RECORD })
            assertTrue(model.results.isEmpty())

            now += 30000
            model.simulation.advanceFrame(now)
            model.navigation.menu()
            model.resume()
            assertEquals(GameScreen.VICTORY, model.screen)
            assertEquals(1, music.modes.count { it == MusicMode.RECORD })

            model.nextRound()
            assertEquals(GameScreen.PLAYING, model.screen)
            assertEquals(80000, model.game!!.score)
            assertEquals(100, model.game!!.lines)
            assertEquals(initialGravity, model.game!!.gravityMillis)
            assertEquals(11, model.game!!.level)
            assertEquals(1, model.game!!.completedRounds)
            assertEquals(0, model.game!!.roundFruits)
            assertTrue(model.game!!.board.flatten().all { it == null })
            val state = model.game
            model.nextRound()
            assertEquals(state, model.game)

            repeat(30) { model.input.command(GameCommand.HARD_DROP) }
            assertEquals(1, model.results.size)
            assertEquals(1, model.results.single().completedRounds)
            assertEquals(100L, model.results.single().durationMillis)
        }
    }
}
