package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

class DifficultyTest {
    @Test fun curvesAreMonotonicOrderedAndBounded() {
        for (level in 1..200) {
            val values = Difficulty.entries.map { it.gravityMillis(level) }
            assertTrue(values.zipWithNext().all { (easy, hard) -> easy >= hard })
            Difficulty.entries.forEach {
                assertTrue(it.gravityMillis(level) in 100..800)
                assertTrue(it.gravityMillis(level + 1) <= it.gravityMillis(level))
            }
        }
    }

    @Test fun gentleBananaAndDifficultFinalFruit() {
        assertEquals(9, GameRules.level(16000))
        assertEquals(10, GameRules.level(20000))
        assertEquals(23, GameRules.level(GameRules.ROUND_SCORE))
        assertTrue(Difficulty.MEDIUM.gravityMillis(9) in 440..450)
        assertTrue(Difficulty.MEDIUM.gravityMillis(10) in 410..420)
        Difficulty.entries.forEach {
            assertEquals(800L, it.gravityMillis(1))
            assertEquals(it.finalMillis, it.gravityMillis(23))
            assertEquals(100L, it.gravityMillis(200))
        }
    }

    @Test fun difficultySurvivesCopiesSpawnsAndRounds() {
        val engine = GameEngine(kotlin.random.Random(3))
        val state = engine.newGame(Difficulty.HARD)
        assertEquals(Difficulty.HARD, engine.apply(state, GameCommand.HARD_DROP).difficulty)
        val victory = state.copy(score = 80000, victoryPending = true)
        assertEquals(Difficulty.HARD, engine.nextRound(victory).difficulty)
        assertEquals(Difficulty.MEDIUM, Difficulty.restore(null))
    }
}
