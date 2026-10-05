package ru.itoltec.swypetris

/** Shared version-5 rules for engine, HUD, and help; thresholds avoid Int overflow. */
object GameRules {
    const val VERSION = 5
    const val PREVIOUS_VERSION = 4
    const val FRUIT_STEP = 10_000
    const val ROUND_SCORE = FRUIT_STEP * 8

    /** Returns the cumulative score threshold for [level], starting with level one. */
    fun threshold(level: Int): Long {
        val n = level.coerceAtLeast(1).toLong() - 1
        return 1000L * n + 125L * n * (n - 1)
    }

    /** Finds the level for total [score], even when one award crosses several thresholds. */
    fun level(score: Int): Int {
        var low = 1
        var high = 5000 // Bound above the maximum positive Int score.
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (threshold(middle) <= score.coerceAtLeast(0).toLong()) low = middle else high = middle - 1
        }
        return low
    }

    /** Returns the nearest score threshold not yet reached. */
    fun nextThreshold(score: Int): Long = threshold(level(score) + 1)

    /** Fraction of the current level interval completed, between zero and one. */
    fun progress(score: Int): Float {
        val start = threshold(level(score))
        return ((score.coerceAtLeast(0).toLong() - start).toDouble() / (nextThreshold(score) - start)).toFloat()
    }

    /** Tests the final 10% of a level interval with integer arithmetic to avoid rounding drift. */
    fun nearingLevel(score: Int): Boolean {
        val start = threshold(level(score))
        return (score.toLong() - start) * 10 >= (nextThreshold(score) - start) * 9
    }

    /** Displays either the score or negative points remaining to the next level. */
    fun displayScore(score: Int): String = if (nearingLevel(score)) "−${nextThreshold(score) - score}" else "$score"

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

    /** Returns the gravity interval for [level] using the selected [difficulty]. */
    fun gravityMillis(level: Int, difficulty: Difficulty = Difficulty.MEDIUM): Long = difficulty.gravityMillis(level)
}
