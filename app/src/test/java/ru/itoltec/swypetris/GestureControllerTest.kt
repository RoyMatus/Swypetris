package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

/** Проверяет распознавание на виртуальном времени без задержек и Android. */
class GestureControllerTest {
    private val commands = mutableListOf<GameCommand>()
    private val gestures = GestureController(GestureConfig()) { commands += it }

    /** Начальный свайп вниз с последующим удержанием остаётся мягким спуском, даже если он быстрый. */
    @Test fun initialFastDownSwipeCanStillBeHeld() {
        gestures.down(100f, 100f, 0)
        gestures.move(100f, 130f, 30)
        gestures.move(100f, 190f, 70)
        assertTrue(commands.isEmpty())
        gestures.advance(300)
        gestures.up(100f, 190f, 320)
        assertEquals(listOf(GameCommand.SOFT_DROP), commands)
    }

    /** Рывок вниз после удержания и бокового движения бросает ровно одну фигуру до отпускания. */
    @Test fun downwardFlickDuringHoldDropsImmediatelyOnce() {
        gestures.down(100f, 100f, 0)
        gestures.advance(500)
        gestures.move(140f, 100f, 550)
        gestures.advance(1000)
        gestures.move(142f, 120f, 1020)
        gestures.move(144f, 150f, 1050)
        assertEquals(GameCommand.HARD_DROP, commands.last())
        assertFalse(gestures.softDropping)
        val count = commands.size
        gestures.move(180f, 250f, 1100)
        gestures.advance(1500)
        gestures.up(180f, 250f, 1550)
        assertEquals(count, commands.size)
        assertEquals(1, commands.count { it == GameCommand.HARD_DROP })
    }

    /** Скорость считается по недавнему отрезку, а медленный спуск и диагональ вбок не бросают. */
    @Test fun slowMotionAndHorizontalDiagonalDoNotFlick() {
        gestures.down(100f, 100f, 0)
        gestures.advance(500)
        for (step in 1..10) gestures.move(100f, 100f + step * 10, 500L + step * 80)
        gestures.move(180f, 240f, 1340)
        gestures.up(180f, 240f, 1350)
        assertFalse(commands.contains(GameCommand.HARD_DROP))
    }

    /** Бросок работает после одного бокового шага и неподвижного спуска; отмена очищает историю. */
    @Test fun flickWorksFromBothHoldModesAndResets() {
        gestures.down(0f, 0f, 0)
        gestures.move(30f, 0f, 20)
        gestures.move(31f, 40f, 70)
        assertEquals(listOf(GameCommand.RIGHT, GameCommand.HARD_DROP), commands)
        gestures.down(0f, 0f, 1000)
        gestures.advance(1500)
        gestures.move(0f, 40f, 1550)
        assertEquals(GameCommand.HARD_DROP, commands.last())
        gestures.down(0f, 0f, 2000)
        gestures.cancel()
        gestures.up(0f, 100f, 2040)
        assertEquals(2, commands.count { it == GameCommand.HARD_DROP })
    }

    /** Боковые шаги после долгого удержания не откладывают ни один шаг мягкого спуска. */
    @Test fun stationaryDropAndHorizontalMotionHaveIndependentClocks() {
        gestures.down(100f, 50f, 0)
        gestures.advance(500)
        gestures.move(107f, 50f, 540)
        assertEquals(listOf(GameCommand.SOFT_DROP), commands)
        gestures.move(108f, 50f, 550)
        gestures.move(140f, 50f, 590)
        gestures.advance(600)
        assertEquals(listOf(GameCommand.SOFT_DROP, GameCommand.RIGHT, GameCommand.RIGHT, GameCommand.SOFT_DROP), commands)
        gestures.move(134f, 50f, 650)
        gestures.advance(700)
        assertEquals(listOf(GameCommand.LEFT, GameCommand.SOFT_DROP), commands.takeLast(2))
        gestures.up(134f, 50f, 750)
        gestures.advance(2000)
        assertFalse(commands.contains(GameCommand.HARD_DROP))
        assertEquals(6, commands.size)
    }

    /** Свайп вниз также допускает боковое движение, а дрожание до удержания не сдвигает таймер. */
    @Test fun downSwipeCanMoveSidewaysAndCancelBothAxes() {
        gestures.down(100f, 0f, 0)
        gestures.move(100f, 30f, 10)
        gestures.move(101f, 31f, 200)
        gestures.advance(260)
        assertEquals(listOf(GameCommand.SOFT_DROP), commands)
        gestures.move(92f, 30f, 300)
        gestures.advance(360)
        assertEquals(listOf(GameCommand.SOFT_DROP, GameCommand.LEFT, GameCommand.SOFT_DROP), commands)
        gestures.cancel()
        gestures.advance(2000)
        assertEquals(3, commands.size)
    }

    /** Если спуск фиксирует фигуру, боковой автоповтор того же кадра не двигает следующую. */
    @Test fun dropCancellationPreventsHorizontalRepeatOnSameFrame() {
        lateinit var controller: GestureController
        var cancelOnDrop = false
        controller = GestureController(GestureConfig()) {
            commands += it
            if (it == GameCommand.SOFT_DROP && cancelOnDrop) controller.cancel()
        }
        controller.down(0f, 0f, 0)
        controller.advance(500)
        controller.move(8f, 0f, 600)
        cancelOnDrop = true
        controller.advance(900)
        assertEquals(listOf(GameCommand.SOFT_DROP, GameCommand.RIGHT, GameCommand.SOFT_DROP), commands)
    }

    /** Выполняет короткое касание в заданный момент. */
    private fun tap(time: Long) {
        gestures.down(0f, 0f, time)
        gestures.up(0f, 0f, time + 20)
    }

    /** Удержание без движения начинает спуск через 500 мс и повторяет шаг раз в 100 мс. */
    @Test fun stationaryHoldDropsAtHalfPreviousSpeed() {
        gestures.down(50f, 50f, 0)
        gestures.advance(499)
        assertTrue(commands.isEmpty())
        gestures.advance(500)
        assertEquals(listOf(GameCommand.SOFT_DROP), commands)
        gestures.advance(599)
        assertEquals(1, commands.size)
        gestures.advance(600)
        assertEquals(2, commands.size)
        gestures.up(50f, 50f, 650)
        gestures.advance(1000)
        assertEquals(List(2) { GameCommand.SOFT_DROP }, commands)
    }

    /** Старт после 8 dp, повтор только после 300 мс остановки, затем каждые 100 мс. */
    @Test fun horizontalRepeatTiming() {
        gestures.down(0f, 0f, 0)
        gestures.move(-7.9f, 0f, 5)
        assertTrue(commands.isEmpty())
        gestures.move(-8f, 0f, 10)
        gestures.advance(309)
        assertEquals(listOf(GameCommand.LEFT), commands)
        gestures.advance(310)
        gestures.advance(409)
        assertEquals(2, commands.size)
        gestures.advance(410)
        gestures.up(-8f, 0f, 420)
        gestures.advance(1000)
        assertEquals(List(3) { GameCommand.LEFT }, commands)
    }

    /** Правый свайп до отпускания отправляет только один сдвиг. */
    @Test fun shortRightSwipeMovesOnce() {
        gestures.down(0f, 0f, 0)
        gestures.move(30f, 0f, 10)
        gestures.up(30f, 0f, 40)
        assertEquals(listOf(GameCommand.RIGHT), commands)
    }

    /** Бросок вниз происходит при отпускании до порога удержания. */
    @Test fun shortDownDropsOnRelease() {
        gestures.down(0f, 0f, 0)
        gestures.move(0f, 30f, 10)
        assertTrue(commands.isEmpty())
        gestures.up(0f, 30f, 259)
        assertEquals(listOf(GameCommand.HARD_DROP), commands)
    }

    /** На границе удержания начинается мягкий спуск, отпускание не вызывает бросок. */
    @Test fun heldDownNeverHardDrops() {
        gestures.down(0f, 0f, 0)
        gestures.move(0f, 30f, 10)
        gestures.advance(260)
        assertTrue(gestures.softDropping)
        gestures.advance(359)
        assertEquals(1, commands.size)
        gestures.advance(360)
        gestures.up(0f, 30f, 370)
        gestures.advance(900)
        assertEquals(List(2) { GameCommand.SOFT_DROP }, commands)
        assertFalse(gestures.softDropping)
    }

    /** Даже если таймер ещё не вызывался, отпускание на границе удержания не бросает фигуру. */
    @Test fun releaseAtHoldBoundaryIsSoftDrop() {
        gestures.down(0f, 0f, 0)
        gestures.move(0f, 30f, 10)
        gestures.up(0f, 30f, 260)
        assertEquals(listOf(GameCommand.SOFT_DROP), commands)
    }

    /** Обе верхние диагонали вращают один раз, направление фиксируется до отпускания. */
    @Test fun diagonalRotationsDoNotRepeatOrChangeDirection() {
        for (dx in listOf(-30f, 30f)) {
            gestures.down(0f, 0f, 0)
            gestures.move(dx, -30f, 10)
            gestures.move(100f, 0f, 20)
            gestures.advance(500)
            gestures.up(100f, 0f, 600)
        }
        assertEquals(listOf(GameCommand.COUNTERCLOCKWISE, GameCommand.CLOCKWISE), commands)
    }

    /** Одиночный тап вращает сразу при отпускании и не создаёт отложенных команд. */
    @Test fun singleTapRotatesImmediately() {
        tap(0)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
        gestures.advance(1000)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
    }

    /** Два быстрых тапа выполняют два поворота и не открывают паузу. */
    @Test fun twoTapsRotateTwice() {
        tap(0)
        tap(100)
        gestures.advance(1000)
        assertEquals(List(2) { GameCommand.CLOCKWISE }, commands)
    }

    /** Неподвижное удержание включает мягкий спуск; свайп вверх вращает один раз. */
    @Test fun longPressDoesNothingAndUpRotatesOnce() {
        gestures.down(0f, 0f, 0)
        gestures.up(0f, 0f, 600)
        assertEquals(listOf(GameCommand.SOFT_DROP), commands)
        commands.clear()
        gestures.down(0f, 0f, 1000)
        gestures.move(0f, -24f, 1010)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
        gestures.advance(1700)
        gestures.up(0f, -60f, 1800)
        gestures.advance(2000)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
    }

    /** Отмена запрещает поворот при отпускании и прекращает повторы удержания. */
    @Test fun cancellationClearsTimers() {
        gestures.down(0f, 0f, 0)
        gestures.cancel()
        gestures.up(0f, 0f, 20)
        gestures.advance(1000)
        assertTrue(commands.isEmpty())
        gestures.down(0f, 0f, 2000)
        gestures.move(30f, 0f, 2010)
        gestures.cancel()
        gestures.advance(3000)
        gestures.up(30f, 0f, 3010)
        assertEquals(listOf(GameCommand.RIGHT), commands)
    }

    /** Синхронная смена фигуры при мягком спуске прекращает жест до отпускания. */
    @Test fun pieceChangeDuringEmitCancelsGesture() {
        lateinit var controller: GestureController
        controller = GestureController(GestureConfig()) {
            commands += it
            controller.cancel()
        }
        controller.down(0f, 0f, 0)
        controller.move(0f, 30f, 10)
        controller.advance(260)
        controller.advance(500)
        controller.up(0f, 30f, 600)
        assertEquals(listOf(GameCommand.SOFT_DROP), commands)
    }

    /** Несколько разворотов работают слева от точки начала и используют обновляемый экстремум. */
    @Test fun reversalsUseExtremePositionAndIgnoreJitter() {
        gestures.down(100f, 0f, 0)
        gestures.move(70f, 0f, 10)
        gestures.move(75.9f, 0f, 30)
        assertEquals(listOf(GameCommand.LEFT), commands)
        gestures.move(76f, 0f, 40)
        assertEquals(listOf(GameCommand.LEFT, GameCommand.RIGHT), commands)
        gestures.move(80f, 0f, 50)
        gestures.move(74.1f, 0f, 60)
        assertEquals(2, commands.size)
        gestures.move(74f, 0f, 70)
        gestures.up(74f, 0f, 80)
        assertEquals(listOf(GameCommand.LEFT, GameCommand.RIGHT, GameCommand.LEFT), commands)
    }

    /** Разворот сразу шагает и заново отсчитывает задержку неподвижного удержания. */
    @Test fun reversalRestartsHoldTimer() {
        gestures.down(100f, 0f, 0)
        gestures.move(70f, 0f, 10)
        gestures.advance(310)
        gestures.move(76f, 0f, 350)
        gestures.advance(410)
        gestures.advance(649)
        assertEquals(listOf(GameCommand.LEFT, GameCommand.LEFT, GameCommand.RIGHT), commands)
        gestures.advance(650)
        gestures.advance(750)
        assertEquals(List(2) { GameCommand.LEFT } + List(3) { GameCommand.RIGHT }, commands)
    }

    /** Упор в стену не мешает развороту того же касания обратно в поле. */
    @Test fun reversalWorksAtWall() {
        val engine = GameEngine()
        var state = GameState(active = Piece(Tetromino.O, x = 0), next = Tetromino.T)
        val controller = GestureController(GestureConfig()) { state = engine.apply(state, it) }
        controller.down(100f, 0f, 0)
        controller.move(70f, 0f, 10)
        controller.move(-200f, 0f, 20)
        controller.advance(260)
        assertEquals(0, state.active.x)
        controller.move(-194f, 0f, 300)
        assertEquals(1, state.active.x)
    }

    /** По обе стороны границ верхних секторов сохраняются вертикальный и диагональный повороты. */
    @Test fun upperSectorBoundaries() {
        for ((angle, expected) in listOf(
            -22.4 to GameCommand.CLOCKWISE, -22.6 to GameCommand.COUNTERCLOCKWISE,
            -67.4 to GameCommand.COUNTERCLOCKWISE, -67.6 to GameCommand.LEFT,
            22.4 to GameCommand.CLOCKWISE, 22.6 to GameCommand.CLOCKWISE,
            67.4 to GameCommand.CLOCKWISE, 67.6 to GameCommand.RIGHT
        )) {
            commands.clear()
            val radians = Math.toRadians(angle)
            gestures.down(0f, 0f, 0)
            gestures.move((40 * kotlin.math.sin(radians)).toFloat(), (-40 * kotlin.math.cos(radians)).toFloat(), 10)
            assertEquals(listOf(expected), commands)
            gestures.cancel()
        }
    }

    /** Путь пальца даёт несколько шагов сразу, а остановка продолжает движение без двойного шага. */
    @Test fun followsDistanceAndReschedulesRepeat() {
        gestures.down(0f, 0f, 0)
        gestures.move(8f, 0f, 10)
        gestures.move(39f, 0f, 20)
        assertEquals(1, commands.size)
        gestures.move(104f, 0f, 30)
        assertEquals(List(4) { GameCommand.RIGHT }, commands)
        gestures.advance(329)
        assertEquals(4, commands.size)
        gestures.advance(330)
        assertEquals(5, commands.size)
        gestures.cancel()
        gestures.advance(1000)
        assertEquals(5, commands.size)
    }

    /** Диагональ не становится горизонтальным движением при пересечении малого порога 8 dp. */
    @Test fun diagonalWaitsForItsOwnThreshold() {
        gestures.down(0f, 0f, 0)
        gestures.move(-8f, -8f, 10)
        assertTrue(commands.isEmpty())
        gestures.move(-20f, -20f, 20)
        assertEquals(listOf(GameCommand.COUNTERCLOCKWISE), commands)
    }
}
