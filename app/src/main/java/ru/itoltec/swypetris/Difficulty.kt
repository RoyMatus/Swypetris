package ru.itoltec.swypetris

import kotlin.math.pow
import kotlin.math.roundToLong

/** Early progression stays gentle; the last fruit is a deliberate late-game challenge. */
enum class Difficulty(val id: String, val title: String, val earlyMultiplier: Double, val finalMillis: Long) {
    EASY("easy", "Лёгкая", 0.95, 180),
    MEDIUM("medium", "Средняя", 0.93, 120),
    HARD("hard", "Сложная", 0.90, 100);

    fun gravityMillis(level: Int): Long {
        val steps = (level - 1).coerceAtLeast(0)
        val earlySteps = EARLY_LAST_LEVEL - 1
        val atTransition = INITIAL_MILLIS * earlyMultiplier.pow(earlySteps)
        val finalLevel = GameRules.level(GameRules.ROUND_SCORE)
        val interval = if (level <= EARLY_LAST_LEVEL) INITIAL_MILLIS * earlyMultiplier.pow(steps)
        else atTransition * (finalMillis / atTransition).pow(
            (level - EARLY_LAST_LEVEL).toDouble() / (finalLevel - EARLY_LAST_LEVEL))
        return interval.roundToLong().coerceAtLeast(MINIMUM_MILLIS)
    }

    companion object {
        const val INITIAL_MILLIS = 800L
        const val MINIMUM_MILLIS = 100L
        const val EARLY_LAST_LEVEL = 10
        fun find(id: String?): Difficulty? = entries.firstOrNull { it.id == id }
        fun restore(id: String?): Difficulty = find(id) ?: MEDIUM
    }
}
