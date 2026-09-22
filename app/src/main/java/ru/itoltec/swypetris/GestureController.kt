package ru.itoltec.swypetris

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Пороги распознавания: расстояния в dp, интервалы в миллисекундах монотонных часов. */
data class GestureConfig(
    val swipeDistance: Float = 24f,
    val horizontalStartDistance: Float = 8f,
    val horizontalStepDistance: Float = 32f,
    val reversalDistance: Float = 6f,
    val tapSlop: Float = 8f,
    val longPressMillis: Long = 500,
    val holdMillis: Long = 250,
    val horizontalHoldMillis: Long = 300,
    val repeatMillis: Long = 100,
    val softDropMillis: Long = 100,
    val flickDistance: Float = 32f,
    val flickVelocity: Float = 600f,
    val flickWindowMillis: Long = 120
)

/** Преобразует касания в команды; [emit] может синхронно отменить жест при смене фигуры. */
class GestureController(private val config: GestureConfig, private val emit: (GameCommand) -> Unit) {
    private var down = false
    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var moved = false
    private var direction: GameCommand? = null
    private var recognized = false
    private var extremeX = 0f
    private var stepAnchorX = 0f
    private var lastX = 0f
    private var recognizedAt = 0L
    private var repeatAt = 0L
    private var dropAt = 0L
    private var pointerX = 0f
    private var pointerY = 0f
    private val recentMotion = ArrayDeque<MotionPoint>()

    /** Точка короткой истории движения для распознавания рывка независимо от длительности удержания. */
    private data class MotionPoint(val x: Float, val y: Float, val time: Long)
    var softDropping = false
        private set

    /** Сбрасывает касание и удержание без отправки игровых команд. */
    fun cancel() {
        down = false
        direction = null
        recognized = false
        softDropping = false
        recentMotion.clear()
    }

    /** Начинает касание; само нажатие не вращает фигуру, поскольку может оказаться свайпом. */
    fun down(x: Float, y: Float, time: Long) {
        cancel()
        down = true
        startX = x
        startY = y
        lastX = x
        stepAnchorX = x
        downTime = time
        moved = false
        trackMotion(x, y, time)
    }

    /** Распознаёт свайп и резкий бросок вниз из удержания или бокового движения без отрыва пальца. */
    fun move(x: Float, y: Float, time: Long) {
        if (!down) return
        val canFlick = recognized && (direction == GameCommand.LEFT || direction == GameCommand.RIGHT || softDropping)
        val flick = canFlick && recentMotion.any { point ->
            val elapsed = time - point.time
            val distance = y - point.y
            elapsed in 1..config.flickWindowMillis && distance >= config.flickDistance &&
                distance > abs(x - point.x) * 1.3f && distance * 1000 / elapsed >= config.flickVelocity
        }
        trackMotion(x, y, time)
        if (flick) {
            cancel() // Бросок завершает касание, его остаток не управляет следующей фигурой.
            emit(GameCommand.HARD_DROP)
            return
        }
        val dx = x - startX
        val dy = y - startY
        if (hypot(dx, dy) > config.tapSlop) moved = true
        if (recognized) {
            updateHorizontalDirection(x, time)
            return
        }
        val angle = Math.toDegrees(atan2(abs(dx).toDouble(), (-dy).toDouble()))
        val diagonal = dy < 0 && angle in 22.5..67.5
        val horizontal = !diagonal && abs(dx) > abs(dy)
        if (hypot(dx, dy) < if (horizontal) config.horizontalStartDistance else config.swipeDistance) return
        recognized = true
        direction = when {
            dy < 0 && angle in 22.5..67.5 -> if (dx < 0) GameCommand.COUNTERCLOCKWISE else GameCommand.CLOCKWISE
            abs(dx) > abs(dy) -> if (dx < 0) GameCommand.LEFT else GameCommand.RIGHT
            dy > 0 -> GameCommand.SOFT_DROP
            else -> GameCommand.CLOCKWISE // Свайп вверх действует как одиночный тап.
        }
        extremeX = x
        stepAnchorX = x
        lastX = x
        recognizedAt = time
        repeatAt = time + if (horizontal) config.horizontalHoldMillis else config.holdMillis
        if (direction in listOf(GameCommand.LEFT, GameCommand.RIGHT, GameCommand.CLOCKWISE, GameCommand.COUNTERCLOCKWISE)) emit(direction!!)
    }

    /** Хранит только короткое окно движения; неподвижное удержание не снижает скорость нового рывка. */
    private fun trackMotion(x: Float, y: Float, time: Long) {
        pointerX = x
        pointerY = y
        while (recentMotion.isNotEmpty() && time - recentMotion.first().time > config.flickWindowMillis) {
            recentMotion.removeFirst()
        }
        if (recentMotion.lastOrNull()?.time != time) recentMotion.addLast(MotionPoint(x, y, time))
    }

    /**
     * Отсчитывает разворот от крайней достигнутой позиции, а не от начала касания.
     * Шаг зависит от ширины клетки; мягкий спуск имеет собственный независимый таймер.
     */
    private fun updateHorizontalDirection(x: Float, time: Long) {
        if (direction == GameCommand.SOFT_DROP && softDropping &&
            abs(x - stepAnchorX) >= config.horizontalStartDistance) {
            direction = if (x < stepAnchorX) GameCommand.LEFT else GameCommand.RIGHT
            extremeX = x
            stepAnchorX = x
            lastX = x
            repeatAt = time + config.horizontalHoldMillis
            emit(direction!!)
            return
        }
        if (direction != GameCommand.LEFT && direction != GameCommand.RIGHT) return
        if (x != lastX) repeatAt = time + config.horizontalHoldMillis
        lastX = x
        val reversed = when (direction) {
            GameCommand.LEFT -> {
                extremeX = minOf(extremeX, x)
                if (x - extremeX >= config.reversalDistance) GameCommand.RIGHT else null
            }
            GameCommand.RIGHT -> {
                extremeX = maxOf(extremeX, x)
                if (extremeX - x >= config.reversalDistance) GameCommand.LEFT else null
            }
            else -> null
        }
        if (reversed != null) {
            direction = reversed
            extremeX = x
            stepAnchorX = x
            repeatAt = time + config.horizontalHoldMillis
            emit(reversed)
            return
        }
        val action = direction
        if (action != GameCommand.LEFT && action != GameCommand.RIGHT) return
        val sign = if (action == GameCommand.LEFT) -1 else 1
        val steps = (((x - stepAnchorX) * sign) / config.horizontalStepDistance).toInt().coerceIn(0, 10)
        if (steps == 0) return
        stepAnchorX += steps * config.horizontalStepDistance * sign
        // Большой скачок у стены не накапливает невыполненные команды.
        if (steps == 10) stepAnchorX = x
        repeatAt = time + config.horizontalHoldMillis
        repeat(steps) {
            if (!down) return
            emit(action)
        }
    }

    /** Обрабатывает удержание даже при неподвижном пальце; пропущенные кадры не создают рывок. */
    fun advance(time: Long) {
        if (!down) return
        trackMotion(pointerX, pointerY, time)
        if (!recognized && !moved && time - downTime >= config.longPressMillis) {
            recognized = true
            direction = GameCommand.SOFT_DROP
            recognizedAt = time
            repeatAt = time
        }
        if (!softDropping && direction == GameCommand.SOFT_DROP && time >= repeatAt) {
            softDropping = true
            dropAt = time
        }
        if (softDropping && time >= dropAt) {
            dropAt = time + config.softDropMillis
            emit(GameCommand.SOFT_DROP)
        }
        if (!down) return // Фиксация фигуры во время спуска отменяет также боковой шаг.
        val action = direction
        if (action in listOf(GameCommand.LEFT, GameCommand.RIGHT) && time >= repeatAt) {
            repeatAt = time + config.repeatMillis
            emit(action!!)
        }
    }

    /** Завершает жест: тап сразу вращает, короткий свайп вниз бросает, удержание отключает ускорение. */
    fun up(x: Float, y: Float, time: Long) {
        move(x, y, time)
        advance(time)
        if (!down) return
        down = false
        val action = direction
        direction = null
        val wasSoftDropping = softDropping
        softDropping = false
        if (action == GameCommand.SOFT_DROP && !wasSoftDropping && time - recognizedAt < config.holdMillis) {
            emit(GameCommand.HARD_DROP)
        } else if (!recognized && !moved && time - downTime < config.longPressMillis) {
            emit(GameCommand.CLOCKWISE)
        }
    }
}

