package ru.itoltec.swypetris

import kotlin.math.pow
import kotlin.math.roundToLong

private const val GRAVITY_FLOOR_STEP = 18
private const val GRAVITY_BASE_SECONDS = 0.8
private const val GRAVITY_STEP_SECONDS = 0.007
private const val NANOS_PER_SECOND = 1_000_000_000
internal const val MAX_PLAYER_NAME_LENGTH = 40


/** Shared version-6 rules for engine, HUD, and help; thresholds avoid Int overflow. */
object GameRules {
    const val MAX_CLEAR_LINES = 4
    const val VERSION = 6
    const val PREVIOUS_VERSION = 5
    const val NANOS_PER_MILLI = 1_000_000L
    // Rounded upward to keep the effective rate at or below 20G.
    const val MIN_GRAVITY_NANOS = 833_334L
    const val FRUIT_STEP = 10_000
    const val ROUND_SCORE = FRUIT_STEP * 8
    const val MAX_SCORE = 999_999

    const val LINES_PER_LEVEL = 10
    const val GRAVITY_PROGRESSION_STRETCH = 3.0
    const val MIN_STARTING_LEVEL = 1
    const val MAX_STARTING_LEVEL = 15

    /** Cumulative cleared-line threshold; Long keeps even extreme inputs safe. */
    fun threshold(level: Int): Long = (level.coerceAtLeast(1).toLong() - 1) * LINES_PER_LEVEL

    /** A higher start waits for its cumulative goal before ten-line transitions begin. */
    fun level(lines: Int, startingLevel: Int = 1): Int = maxOf(startingLevel,
        1 + lines.coerceAtLeast(0) / LINES_PER_LEVEL)

    fun nextThreshold(lines: Int, startingLevel: Int = 1): Long = threshold(level(lines, startingLevel) + 1)

    fun progress(lines: Int, startingLevel: Int = 1): Float {
        val current = level(lines, startingLevel)
        val previous = if (current == startingLevel) 0L else threshold(current)
        return (lines.coerceAtLeast(0).toLong() - previous).toFloat() / (threshold(current + 1) - previous)
    }

    /** Only the final line before the actual next goal drives the HUD cue. */
    fun nearingLevel(lines: Int, startingLevel: Int = 1): Boolean =
        lines.toLong() == nextThreshold(lines, startingLevel) - 1

    /** Adds earned [points] to [score] without overflowing Int; negative awards are ignored. */
    fun add(score: Int, points: Int): Int = (score.toLong() + points.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE
        .toLong()).toInt()

    /** Drop awards are independent of level; gravity never awards points. */
    fun dropScore(command: GameCommand, cells: Int): Int {
        val rate = when (command) {
            GameCommand.SOFT_DROP -> 1
            GameCommand.HARD_DROP -> 2
            else -> 0
        }
        return (cells.coerceAtLeast(0).toLong() * rate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Base award for an ordinary clear, before the level multiplier. */
    private val ordinaryClearAwards = listOf(0, 100, 300, 500, 800)

    fun lineScore(count: Int): Int = ordinaryClearAwards[count]

    /** The event captures the level before removal; drop points were already awarded. */
    fun placementScore(event: PlacementResult): Int {
        val base = when (event.spin) {
            Spin.NONE -> lineScore(event.lines)
            Spin.MINI -> listOf(100, 200, 400, 1600, 0)[event.lines]
            Spin.FULL -> listOf(400, 800, 1200, 1600, 0)[event.lines]
        }.toLong()
        val clear = if (event.difficult && event.backToBack) base * 3 / 2 else base
        val combo = if (event.lines > 0) 50L * event.combo.coerceAtLeast(0) else 0L
        val perfect = if (!event.perfectClear) 0 else when (event.lines) {
            1 -> 800
            2 -> 1200
            3 -> 1800
            4 -> if (event.backToBack) 3200 else 2000
            else -> 0
        }
        // Clamp the unscaled sum first: once it exceeds Int max, every positive level does too.
        return ((clear + combo + perfect).coerceAtMost(Int.MAX_VALUE.toLong()) * event.level.coerceAtLeast(1))
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Marathon curve stretched after the selected start; scoring levels stay unchanged. */
    fun gravityNanos(level: Int, startingLevel: Int = 1): Long {
        val start = startingLevel.coerceAtLeast(1)
        val steps = start - 1 + (level.coerceAtLeast(start) - start) / GRAVITY_PROGRESSION_STRETCH
        if (steps >= GRAVITY_FLOOR_STEP) return MIN_GRAVITY_NANOS
        return ((GRAVITY_BASE_SECONDS - steps * GRAVITY_STEP_SECONDS).pow(steps) * NANOS_PER_SECOND).roundToLong()
            .coerceAtLeast(MIN_GRAVITY_NANOS)
    }

    /** Rounded up for scheduling/display only; simulation retains nanosecond precision. */
    fun gravityMillis(level: Int, startingLevel: Int = 1): Long =
        (gravityNanos(level, startingLevel) + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI
}

/** Only score is capped at absolute victory; line counters retain their own overflow bound. */
internal fun GameRules.addScore(score: Int, points: Int): Int = add(score, points).coerceAtMost(MAX_SCORE)
