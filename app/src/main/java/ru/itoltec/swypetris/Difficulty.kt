package ru.itoltec.swypetris

import kotlin.math.pow
import kotlin.math.roundToLong

/** Early progression stays gentle; the last fruit is a deliberate late-game challenge. */
enum class Difficulty(val id: String, val title: String, val earlyMultiplier: Double, val finalMillis: Long) {
    EASY("easy", "Лёгкая", 0.95, 180),
    MEDIUM("medium", "Средняя", 0.93, 120),
    HARD("hard", "Сложная", 0.90, 100);

    /**
     * Calculates the gravity interval for [level]. The first ten levels decay gently according
     * to [earlyMultiplier], then interpolate toward [finalMillis] at the round's final level.
     * The result never drops below [MINIMUM_MILLIS].
     */
    fun gravityMillis(level: Int): Long {
        val steps = (level - 1).coerceAtLeast(0)
        val earlySteps = EARLY_LAST_LEVEL - 1
        val atTransition = INITIAL_MILLIS * earlyMultiplier.pow(earlySteps)
        val finalLevel = GameRules.level(GameRules.ROUND_SCORE)
        val interval = if (level <= EARLY_LAST_LEVEL) INITIAL_MILLIS * earlyMultiplier.pow(steps)
        else {
            val progress = (level - EARLY_LAST_LEVEL).toDouble() / (finalLevel - EARLY_LAST_LEVEL)
            // Reserve more of Medium's acceleration for later levels without changing its endpoints.
            val shapedProgress = if (this == MEDIUM) progress.pow(MEDIUM_LATE_EXPONENT) else progress
            atTransition * (finalMillis / atTransition).pow(shapedProgress)
        }
        return interval.roundToLong().coerceAtLeast(MINIMUM_MILLIS)
    }

    companion object {
        const val INITIAL_MILLIS = 800L
        const val MINIMUM_MILLIS = 100L
        const val EARLY_LAST_LEVEL = 10
        private const val MEDIUM_LATE_EXPONENT = 1.75
        /** Looks up a persisted difficulty ID, returning null for unknown values. */
        fun find(id: String?): Difficulty? = entries.firstOrNull { it.id == id }
        /** Restores a persisted difficulty, falling back to Medium for missing or unknown IDs. */
        fun restore(id: String?): Difficulty = find(id) ?: MEDIUM
    }
}
