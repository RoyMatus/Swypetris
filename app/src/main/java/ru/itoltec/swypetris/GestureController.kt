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
    val dropRatio: Float = 2f,
    val rotationRearmDistance: Float = 12f,
    val holdMillis: Long = 300,
    val holdSlop: Float = 8f
)

/** One pointer controls play; a stationary hold followed by an upward swipe exchanges Hold. */
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
    private var rotationArmed = true
    private var rotationX = 0f
    private var rotationY = 0f
    private var rotationTop = 0f
    private var rotationTopX = 0f
    private var dropArmed = true
    private var dropBottom = 0f
    private var holdCandidate = false
    private var holdReady = false
    var onHoldReady: () -> Unit = {}
    val holdDeadline: Long? get() = if (down && enabled && holdCandidate && !holdReady) downTime + config.holdMillis else null

    /** Readiness itself never moves a piece and emits at most one feedback pulse. */
    fun advanceTime(time: Long) {
        val deadline = holdDeadline ?: return
        if (time >= deadline) {
            holdReady = true
            tapEligible = false
            onHoldReady()
        }
    }

    /** Discards the current touch and re-arms gestures without emitting a command. */
    fun cancel() {
        down = false
        tapEligible = false
        holdCandidate = false
        holdReady = false
        horizontalDirection = 0
        rotationArmed = true
        dropArmed = true
        epoch++
    }

    /** Begins a pointer gesture at dp coordinates [x], [y] and uptime [time]. */
    fun down(x: Float, y: Float, time: Long) {
        cancel()
        down = true
        this.x = x
        this.y = y
        touchX = x
        touchY = y
        downTime = time
        tapEligible = enabled
        holdCandidate = enabled
        rebase()
    }

    /** Rebase at the current pointer, without synthesizing a touch or an action. */
    fun onPieceChanged() {
        tapEligible = false
        holdCandidate = false
        holdReady = false
        rebase()
        epoch++
    }

    /** During line clearing, track the pointer but discard accumulated motion. */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        onPieceChanged()
    }

    /** Resets displacement anchors to the current pointer after a piece or gesture transition. */
    private fun rebase() {
        anchorX = x
        anchorY = y
        horizontalDirection = 0
        rotationX = x
        rotationY = y
        rotationTop = y
        rotationTopX = x
        if (dropArmed) dropBottom = y
    }

    /**
     * Up-left rotates counterclockwise; up/up-right rotate clockwise. Downward strokes hard drop.
     * Reversals and piece changes reset anchors so previous motion cannot trigger the next piece.
     * Holding still never repeats a command.
     */
    fun move(x: Float, y: Float, time: Long) {
        if (!down) return
        advanceTime(time)
        if (!holdReady && hypot(x - touchX, y - touchY) > config.holdSlop) holdCandidate = false
        if (holdReady && touchY - y >= maxOf(config.rotationDistance, config.tapSlop * 2) &&
            touchY - y >= abs(x - touchX) * config.directionRatio) {
            cancel()
            emit(GameCommand.HOLD)
            return
        }
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
        if (!dropArmed) {
            if (y > dropBottom) dropBottom = y
            if (dropBottom - y >= maxOf(config.rotationRearmDistance, config.tapSlop)) {
                dropArmed = true
                rebase()
            } else if (eventDy > 0 && eventDy >= abs(eventDx) * config.directionRatio) {
                // Motion continuing after a drop belongs to the previous piece.
                anchorX = x
                anchorY = y
                horizontalDirection = 0
            }
        }
        if (!rotationArmed) {
            if (y < rotationTop) {
                rotationTop = y
                rotationTopX = x
                // Suppressed upward motion must not accumulate a debt for a subsequent drop.
                if (-eventDy >= abs(eventDx)) {
                    anchorX = x
                    anchorY = y
                    horizontalDirection = 0
                }
            }
            if (y - rotationTop >= maxOf(config.rotationRearmDistance, config.tapSlop) &&
                y - rotationTop >= abs(x - rotationTopX) * config.directionRatio) {
                rotationArmed = true
                rotationX = x
                rotationY = y
            }
        } else if (y > rotationY) {
            // The next upward stroke starts at the bottom of the reversal.
            rotationX = x
            rotationY = y
            if (!rotationArmed) {
                rotationTop = y
                rotationTopX = x
            }
        }
        val dx = x - anchorX
        val dy = y - anchorY
        val rotationDy = rotationY - y
        val rotationDx = x - rotationX
        // Reserve upward diagonals before the rotation threshold, including a disarmed stroke.
        val upwardStroke = rotationDy > 0 && rotationDy >= abs(rotationDx) / config.directionRatio &&
            -eventDy >= abs(eventDx) / config.directionRatio
        val horizontalThreshold = if (horizontalDirection == 0)
            maxOf(config.horizontalStartDistance, config.tapSlop) else config.horizontalStepDistance
        val action = when {
            dropArmed && dy >= maxOf(config.dropDistance, config.tapSlop * 4) && dy >= abs(dx) * config.dropRatio -> GameCommand.HARD_DROP
            rotationArmed && rotationDy >= maxOf(config.rotationDistance, config.tapSlop * 2) &&
                upwardStroke -> if (rotationDx <= -maxOf(config.rotationRearmDistance, config.tapSlop))
                    GameCommand.COUNTERCLOCKWISE else GameCommand.CLOCKWISE
            !upwardStroke && abs(dx) >= horizontalThreshold && abs(dx) >= abs(dy) * config.directionRatio ->
                if (dx < 0) GameCommand.LEFT else GameCommand.RIGHT
            else -> return
        }
        tapEligible = false
        holdCandidate = false
        holdReady = false
        val horizontal = action == GameCommand.LEFT || action == GameCommand.RIGHT
        val rotation = action == GameCommand.CLOCKWISE || action == GameCommand.COUNTERCLOCKWISE
        val sign = if (dx < 0) -1 else 1
        val steps = if (horizontal) {
            1 + ((abs(dx) - horizontalThreshold) / config.horizontalStepDistance).toInt().coerceIn(0, 9)
        } else 1
        if (horizontal) {
            anchorX += sign * (horizontalThreshold + (steps - 1) * config.horizontalStepDistance)
            anchorY = y // Horizontal drift cannot accumulate into a destructive gesture.
            horizontalDirection = sign
            rotationX = x
            rotationY = y
        } else rebase()
        if (rotation) rotationArmed = false
        if (action == GameCommand.HARD_DROP) {
            dropArmed = false
            dropBottom = y
        } else if (horizontal || rotation) dropArmed = true
        val before = epoch
        repeat(steps) {
            emit(action)
            if (!down || !enabled || epoch != before) return
        }
    }

    /** Finishes the gesture and emits one soft drop only for a short, unmoved tap. */
    fun up(x: Float, y: Float, time: Long) {
        if (!down) return
        move(x, y, time)
        val tap = enabled && tapEligible && time - downTime in 0..config.tapMillis
        cancel()
        if (tap) emit(GameCommand.SOFT_DROP)
    }
}
