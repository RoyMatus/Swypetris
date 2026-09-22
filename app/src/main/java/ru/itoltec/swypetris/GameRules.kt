package ru.itoltec.swypetris

import kotlin.math.pow

/** Общие правила версии 4 для движка, панели и справки; пороги считаются без переполнения Int. */
object GameRules {
    const val VERSION = 4
    const val PREVIOUS_VERSION = 3
    const val FRUIT_STEP = 10_000
    const val ROUND_SCORE = FRUIT_STEP * 8

    /** Возвращает суммарный порог достижения уровня, начиная с первого. */
    fun threshold(level: Int): Long {
        val n = level.coerceAtLeast(1).toLong() - 1
        return 1000L * n + 125L * n * (n - 1)
    }

    /** Находит уровень по итоговому счёту, включая переход через несколько границ. */
    fun level(score: Int): Int {
        var low = 1
        var high = 5000 // Порог выше максимального положительного Int.
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (threshold(middle) <= score.coerceAtLeast(0).toLong()) low = middle else high = middle - 1
        }
        return low
    }

    /** Возвращает ближайший ещё не достигнутый порог. */
    fun nextThreshold(score: Int): Long = threshold(level(score) + 1)

    /** Доля пройденного интервала текущего уровня, от нуля до единицы. */
    fun progress(score: Int): Float {
        val start = threshold(level(score))
        return ((score.coerceAtLeast(0).toLong() - start).toDouble() / (nextThreshold(score) - start)).toFloat()
    }

    /** Проверяет последние 20% интервала целочисленно, без ошибки округления. */
    fun nearingLevel(score: Int): Boolean {
        val start = threshold(level(score))
        return (score.toLong() - start) * 5 >= (nextThreshold(score) - start) * 4
    }

    /** Форматирует очки или отрицательный остаток до уровня. */
    fun displayScore(score: Int): String = if (nearingLevel(score)) "−${nextThreshold(score) - score}" else "$score"

    /** Начисляет очки за реальные клетки, защищая счёт от переполнения. */
    fun add(score: Int, points: Int): Int = (score.toLong() + points.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Награда за одновременное удаление строк без множителя уровня. */
    fun lineScore(count: Int): Int = listOf(0, 100, 300, 700, 1500)[count]

    /** Интервал обычного падения с нижним пределом 100 мс. */
    fun gravityMillis(level: Int): Long = (800 * 0.85.pow(level - 1)).toLong().coerceAtLeast(100)
}
