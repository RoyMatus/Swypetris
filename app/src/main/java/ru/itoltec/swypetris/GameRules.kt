package ru.itoltec.swypetris

/** Shared version-5 rules for engine, HUD, and help; thresholds avoid Int overflow. */
object GameRules {
    const val VERSION = 5
    const val PREVIOUS_VERSION = 4
    const val FRUIT_STEP = 10_000
    const val ROUND_SCORE = FRUIT_STEP * 8

    const val LINES_PER_LEVEL = 10

    /** Cumulative cleared-line threshold; Long keeps even extreme inputs safe. */
    fun threshold(level: Int): Long = (level.coerceAtLeast(1).toLong() - 1) * LINES_PER_LEVEL

    /** Fixed-goal Marathon: each ten cleared lines advance one level. */
    fun level(lines: Int): Int = 1 + lines.coerceAtLeast(0) / LINES_PER_LEVEL

    fun nextThreshold(lines: Int): Long = threshold(level(lines) + 1)

    fun progress(lines: Int): Float = (lines.coerceAtLeast(0) % LINES_PER_LEVEL).toFloat() / LINES_PER_LEVEL

    /** The final line before the next level drives the HUD cue. */
    fun nearingLevel(lines: Int): Boolean = lines.coerceAtLeast(0) % LINES_PER_LEVEL == LINES_PER_LEVEL - 1

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

    /** Returns the gravity interval for [level] using the selected [difficulty]. */
    fun gravityMillis(level: Int, difficulty: Difficulty = Difficulty.MEDIUM): Long = difficulty.gravityMillis(level)
}
