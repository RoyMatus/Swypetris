package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

/** Проверяет пороги наград и независимость очков от уровня. */
class RewardsTest {
    @Test fun recordDesignExamplesAgreeWithLiveCumulativeRewards() {
        val fixtures = listOf(
            2_466 to List(8) { 0L },
            42_500 to listOf(1L, 1L, 1L, 1L, 0L, 0L, 0L, 0L),
            80_000 to List(8) { 1L },
            90_000 to listOf(2L, 1L, 1L, 1L, 1L, 1L, 1L, 1L),
            788_267 to listOf(10L, 10L, 10L, 10L, 10L, 10L, 9L, 9L),
            999_999 to listOf(13L, 13L, 13L, 12L, 12L, 12L, 12L, 12L)
        )
        fixtures.forEach { (score, counts) ->
            assertEquals(counts, Fruit.entries.map { fruitQuantity(score.toLong(), it) })
            for (rounds in listOf(0, 1, 12)) {
                assertEquals(counts, GameState(active = Piece(Tetromino.O), next = Tetromino.T,
                    score = score.toLong(), completedRounds = rounds.toLong()).fruitCounts)
            }
        }
    }

    /** Все пороги включительны, коллекция повторяется после восьмого фрукта. */
    @Test fun fruitThresholdsAndCycles() {
        assertEquals(0L, fruitCount(9999))
        assertEquals(1L, fruitCount(10000))
        assertEquals(1L, fruitQuantity(10000, Fruit.CHERRY))
        assertEquals(0L, fruitQuantity(10000, Fruit.BANANA))
        assertEquals(2L, fruitQuantity(90000, Fruit.CHERRY))
        assertEquals(1L, fruitQuantity(90000, Fruit.BANANA))
        for (score in listOf(0, 10000, 20000, 80000, 90000, 200000)) {
            assertEquals(fruitCount(score.toLong()), Fruit.entries.sumOf { fruitQuantity(score.toLong(), it) })
        }
    }

    /** A Perfect Clear adds its bonus to the base award at the captured level. */
    @Test fun scoreUsesCapturedLevelAndPerfectClearBonus() {
        val engine = GameEngine()
        val state = engine.newGame().copy(lines = 100, clearingRows = listOf(16, 17, 18, 19).map(BoardGeometry::row))
        assertEquals(30800L, engine.finishClear(state).score)
    }

    /** Cell width controls movement, and holding never creates repeated actions. */
    @Test fun movementUsesCellWidthAndDoesNotRunAhead() {
        for (width in listOf(300f, 600f, 1200f)) {
            val commands = mutableListOf<GameCommand>()
            val step = width / 12f
            val controller = GestureController(GestureConfig(horizontalStepDistance = step)) { commands += it }
            controller.down(0f, 0f, 0)
            controller.move(12f, 0f, 1)
            repeat(10) { index ->
                controller.move(12f + step * (index + 1) / 10f, 0f, 101L + index * 100)
            }
            assertEquals(2, commands.size)
            controller.move(12f + step, 0f, 1300)
            assertEquals(2, commands.size)
            controller.up(12f + step, 0f, 1301)
            assertEquals(2, commands.size)
        }
    }
}

