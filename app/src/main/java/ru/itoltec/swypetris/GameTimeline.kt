package ru.itoltec.swypetris

/** Logical event time, independent of Android callback frequency. All remainders survive pauses. */
internal class GameTimeline(
    var gravityRemainingNanos: Long,
    var lockFractionNanos: Long = 0,
    var clearElapsedNanos: Long = 0
) {
    private val milli = GameRules.NANOS_PER_MILLI
    val clearMillis: Long get() = clearElapsedNanos / milli
    var advancedNanos: Long = 0
        private set

    /** A spawn starts fresh gravity; an allowed grounded move resets the entire lock clock. */
    fun observe(previous: GameState, updated: GameState) {
        if (updated.generation != previous.generation) {
            gravityRemainingNanos = updated.gravityNanos
            lockFractionNanos = 0
            clearElapsedNanos = 0
        } else if (updated.lockResets != previous.lockResets) lockFractionNanos = 0
        if (previous.clearingRows.isEmpty() && updated.clearingRows.isNotEmpty()) clearElapsedNanos = 0
    }

    /** Grounded gravity cannot move cells; advance its phase without waking at 1200 Hz. */
    private fun advanceGravityPhase(elapsed: Long, interval: Long) {
        gravityRemainingNanos = if (elapsed < gravityRemainingNanos) gravityRemainingNanos - elapsed
        else interval - (elapsed - gravityRemainingNanos) % interval
    }

    fun nextEventNanos(state: GameState, engine: GameEngine): Long = when {
        state.clearingRows.isNotEmpty() -> {
            val step = LineClearAnimation.STEP_MILLIS * milli
            minOf((clearElapsedNanos / step + 1) * step,
                LineClearAnimation.TOTAL_MILLIS * milli) - clearElapsedNanos
        }
        engine.grounded(state) -> state.lockRemaining * milli - lockFractionNanos
        else -> gravityRemainingNanos
    }

    /** Spend lock time only after landing, continuing through spawns and clears in event order. */
    fun advance(initial: GameState, elapsedMillis: Long, engine: GameEngine,
        publish: (GameState, GameState) -> Unit): GameState {
        var state = initial
        var budget = elapsedMillis.coerceIn(0, Long.MAX_VALUE / milli) * milli
        advancedNanos = 0
        while (!state.gameOver && !state.victoryPending) {
            val clearing = state.clearingRows.isNotEmpty()
            val grounded = !clearing && engine.grounded(state)
            val untilEvent = when {
                clearing -> LineClearAnimation.TOTAL_MILLIS * milli - clearElapsedNanos
                grounded -> state.lockRemaining * milli - lockFractionNanos
                else -> gravityRemainingNanos
            }
            val spent = minOf(budget, untilEvent)
            val previous = state
            state = spendTime(state, spent, clearing, grounded, engine)
            budget -= spent
            advancedNanos += spent
            if (spent == untilEvent) {
                if (clearing) state = engine.finishClear(state)
                else if (!grounded) {
                    gravityRemainingNanos = state.gravityNanos
                    state = engine.apply(state, GameCommand.TICK)
                }
            }
            observe(previous, state)
            if (state != previous) publish(previous, state)
            if (budget == 0L || spent < untilEvent) break
        }
        return state
    }

    private fun spendTime(state: GameState, spent: Long, clearing: Boolean, grounded: Boolean,
        engine: GameEngine): GameState {
        return when {
            clearing -> { clearElapsedNanos += spent; state }
            grounded -> {
                advanceGravityPhase(spent, state.gravityNanos)
                val lockTime = lockFractionNanos + spent
                lockFractionNanos = lockTime % milli
                engine.advanceLock(state, lockTime / milli)
            }
            else -> { gravityRemainingNanos -= spent; state }
        }
    }

    /** Lifecycle suspension records clocks without moving, locking, or completing a clear. */
    fun freeze(state: GameState, elapsedMillis: Long, engine: GameEngine): GameState {
        val elapsed = elapsedMillis.coerceIn(0, Long.MAX_VALUE / milli) * milli
        return when {
            state.clearingRows.isNotEmpty() -> {
                clearElapsedNanos += minOf(elapsed, LineClearAnimation.TOTAL_MILLIS * milli - clearElapsedNanos)
                state
            }
            !engine.grounded(state) -> {
                gravityRemainingNanos = (gravityRemainingNanos - elapsed).coerceAtLeast(0)
                state
            }
            else -> {
                advanceGravityPhase(elapsed, state.gravityNanos)
                val lockTime = lockFractionNanos + minOf(elapsed, state.lockRemaining * milli - lockFractionNanos)
                val updated = engine.advanceLock(state, lockTime / milli, allowLock = false)
                lockFractionNanos = if (updated.lockRemaining == 0L) 0 else lockTime % milli
                updated
            }
        }
    }
}
