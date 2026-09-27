package ru.itoltec.swypetris

import kotlin.math.abs
import kotlin.math.hypot

/** Distances are dp; Android's touch slop is supplied by the view. */
data class GestureConfig(
    val horizontalStartDistance: Float = 12f,
    val horizontalStepDistance: Float = 32f,
    val rotationDistance: Float = 24f,
    val dropDistance: Float = 48f,
    val tapSlop: Float = 8f,
    val tapMillis: Long = 250,
    val directionRatio: Float = 1.5f,
    val dropRatio: Float = 2f
)

/** One pointer can control several pieces. Only displacement, never holding time, acts. */
class GestureController(private val config: GestureConfig, private val emit: (GameCommand) -> Unit) {
    private var down = false
    private var enabled = true
    private var x = 0f
    private var y = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var downTime = 0L
    private var tapEligible = false
    private var horizontalDirection = 0
    private var epoch = 0

    fun cancel() {
        down = false
        tapEligible = false
        horizontalDirection = 0
        epoch++
    }

    fun down(x: Float, y: Float, time: Long) {
        cancel()
        down = true
        this.x = x
        this.y = y
        touchX = x
        touchY = y
        downTime = time
        tapEligible = enabled
        rebase()
    }

    /** Rebase at the current pointer, without synthesizing a touch or an action. */
    fun onPieceChanged() {
        tapEligible = false
        rebase()
        epoch++
    }

    /** During line clearing, track the pointer but discard accumulated motion. */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        onPieceChanged()
    }

    private fun rebase() {
        anchorX = x
        anchorY = y
        horizontalDirection = 0
    }

    fun move(x: Float, y: Float, time: Long) {
        if (!down) return
        val eventDx = x - this.x
        val eventDy = y - this.y
        // A reversal starts at the extreme point, not at the beginning of the touch.
        if (horizontalDirection != 0 && eventDx * horizontalDirection < 0 && abs(eventDx) > abs(eventDy)) {
            anchorX = this.x
            anchorY = this.y
            horizontalDirection = 0
        }
        this.x = x
        this.y = y
        if (hypot(x - touchX, y - touchY) > config.tapSlop || time - downTime > config.tapMillis) tapEligible = false
        if (!enabled) { rebase(); return }
        val dx = x - anchorX
        val dy = y - anchorY
        val horizontalThreshold = if (horizontalDirection == 0)
            maxOf(config.horizontalStartDistance, config.tapSlop) else config.horizontalStepDistance
        val action = when {
            dy >= maxOf(config.dropDistance, config.tapSlop * 4) && dy >= abs(dx) * config.dropRatio -> GameCommand.HARD_DROP
            -dy >= maxOf(config.rotationDistance, config.tapSlop * 2) && -dy >= abs(dx) * config.directionRatio -> GameCommand.CLOCKWISE
            abs(dx) >= horizontalThreshold && abs(dx) >= abs(dy) * config.directionRatio ->
                if (dx < 0) GameCommand.LEFT else GameCommand.RIGHT
            else -> return
        }
        tapEligible = false
        val horizontal = action == GameCommand.LEFT || action == GameCommand.RIGHT
        val sign = if (dx < 0) -1 else 1
        val steps = if (horizontal) {
            1 + ((abs(dx) - horizontalThreshold) / config.horizontalStepDistance).toInt().coerceIn(0, 9)
        } else 1
        if (horizontal) {
            anchorX += sign * (horizontalThreshold + (steps - 1) * config.horizontalStepDistance)
            anchorY = y // Horizontal drift cannot accumulate into a destructive gesture.
            horizontalDirection = sign
        } else rebase()
        val before = epoch
        repeat(steps) {
            emit(action)
            if (!down || !enabled || epoch != before) return
        }
    }

    fun up(x: Float, y: Float, time: Long) {
        if (!down) return
        move(x, y, time)
        val tap = enabled && tapEligible && time - downTime in 0..config.tapMillis
        cancel()
        if (tap) emit(GameCommand.SOFT_DROP)
    }
}
