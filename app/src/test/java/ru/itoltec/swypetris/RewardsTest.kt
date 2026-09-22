package ru.itoltec.swypetris

import org.junit.Assert.*
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

    /** Четыре строки дают 1500 на любом уровне; награда не зависит от числа ранее удалённых строк. */
    @Test fun scoreDoesNotMultiplyAtHighLevel() {
        val engine = GameEngine()
        val state = engine.newGame().copy(lines = 100, clearingRows = listOf(16, 17, 18, 19))
        assertEquals(1500, engine.finishClear(state).score)
    }

    /** Ширина клетки управляет расстоянием, постоянное движение откладывает автоповтор. */
    @Test fun movementUsesCellWidthAndDoesNotRunAhead() {
        for (width in listOf(300f, 600f, 1200f)) {
            val commands = mutableListOf<GameCommand>()
            val step = width / 12f
            val controller = GestureController(GestureConfig(horizontalStepDistance = step)) { commands += it }
            controller.down(0f, 0f, 0)
            controller.move(8f, 0f, 1)
            repeat(10) { index ->
                controller.move(8f + step * (index + 1) / 10f, 0f, 101L + index * 100)
                controller.advance(101L + index * 100)
            }
            assertEquals(2, commands.size)
            controller.advance(1300)
            assertEquals(2, commands.size)
            controller.advance(1301)
            assertEquals(3, commands.size)
        }
    }
}

