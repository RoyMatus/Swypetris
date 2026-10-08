package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

/** Проверяет пороги наград и независимость очков от уровня. */
class RewardsTest {
    /** Все пороги включительны, коллекция повторяется после восьмого фрукта. */
    @Test fun fruitThresholdsAndCycles() {
        assertEquals(0, fruitCount(9999))
        assertEquals(1, fruitCount(10000))
        assertEquals(1, fruitQuantity(10000, Fruit.CHERRY))
        assertEquals(0, fruitQuantity(10000, Fruit.BANANA))
        assertEquals(2, fruitQuantity(90000, Fruit.CHERRY))
        assertEquals(1, fruitQuantity(90000, Fruit.BANANA))
        for (score in listOf(0, 10000, 20000, 80000, 90000, 200000)) {
            assertEquals(fruitCount(score), Fruit.entries.sumOf { fruitQuantity(score, it) })
        }
    }

    /** A Perfect Clear adds its bonus to the base award at the captured level. */
    @Test fun scoreUsesCapturedLevelAndPerfectClearBonus() {
        val engine = GameEngine()
        val state = engine.newGame().copy(lines = 100, clearingRows = listOf(16, 17, 18, 19).map(BoardGeometry::row))
        assertEquals(30800, engine.finishClear(state).score)
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

