package ru.itoltec.swypetris

import kotlin.math.pow
import kotlin.math.roundToLong

/** Shared version-6 rules for engine, HUD, and help; thresholds avoid Int overflow. */
object GameRules {
    const val VERSION = 6
    const val PREVIOUS_VERSION = 5
    const val NANOS_PER_MILLI = 1_000_000L
    // Rounded upward to keep the effective rate at or below 20G.
    const val MIN_GRAVITY_NANOS = 833_334L
    const val FRUIT_STEP = 10_000
    const val ROUND_SCORE = FRUIT_STEP * 8

    const val LINES_PER_LEVEL = 10
    const val MIN_STARTING_LEVEL = 1
    const val MAX_STARTING_LEVEL = 15

    /** Cumulative cleared-line threshold; Long keeps even extreme inputs safe. */
    fun threshold(level: Int): Long = (level.coerceAtLeast(1).toLong() - 1) * LINES_PER_LEVEL

    /** A higher start waits for its cumulative goal before ten-line transitions begin. */
    fun level(lines: Int, startingLevel: Int = 1): Int = maxOf(startingLevel, 1 + lines.coerceAtLeast(0) / LINES_PER_LEVEL)

    fun nextThreshold(lines: Int, startingLevel: Int = 1): Long = threshold(level(lines, startingLevel) + 1)

    fun progress(lines: Int, startingLevel: Int = 1): Float {
        val current = level(lines, startingLevel)
        val previous = if (current == startingLevel) 0L else threshold(current)
        return (lines.coerceAtLeast(0).toLong() - previous).toFloat() / (threshold(current + 1) - previous)
    }

    /** Only the final line before the actual next goal drives the HUD cue. */
    fun nearingLevel(lines: Int, startingLevel: Int = 1): Boolean =
        lines.toLong() == nextThreshold(lines, startingLevel) - 1

    /** Score remains score; level progress has its own source of truth. */
    fun displayScore(score: Int): String = "$score"

    /** Adds earned [points] to [score] without overflowing Int; negative awards are ignored. */
    fun add(score: Int, points: Int): Int = (score.toLong() + points.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

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
    fun lineScore(count: Int): Int = listOf(0, 100, 300, 500, 800)[count]

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

    /** Tetris Worlds Marathon; levels 19+ retain 20G and the ordinary lock delay. */
    fun gravityNanos(level: Int): Long {
        if (level >= 19) return MIN_GRAVITY_NANOS
        val steps = level.coerceAtLeast(1) - 1
        return ((0.8 - steps * 0.007).pow(steps) * 1_000_000_000).roundToLong()
            .coerceAtLeast(MIN_GRAVITY_NANOS)
    }

    /** Rounded up for scheduling/display only; simulation retains nanosecond precision. */
    fun gravityMillis(level: Int): Long = (gravityNanos(level) + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI
}
