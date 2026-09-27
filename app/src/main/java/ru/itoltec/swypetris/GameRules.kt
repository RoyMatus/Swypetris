package ru.itoltec.swypetris

/** Shared version-4 rules for engine, HUD, and help; thresholds avoid Int overflow. */
object GameRules {
    const val VERSION = 4
    const val PREVIOUS_VERSION = 3
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

    /** Base award for clearing [count] rows at once, without a level multiplier. */
    fun lineScore(count: Int): Int = listOf(0, 100, 300, 700, 1500)[count]

    /** Returns the gravity interval for [level] using the selected [difficulty]. */
    fun gravityMillis(level: Int, difficulty: Difficulty = Difficulty.MEDIUM): Long = difficulty.gravityMillis(level)
}
