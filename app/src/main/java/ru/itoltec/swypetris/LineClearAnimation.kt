package ru.itoltec.swypetris

/** Последовательное удаление десяти клеток с интервалом 60 мс. */
object LineClearAnimation {
    const val STEP_MILLIS = 60L
    const val TOTAL_MILLIS = 600L

    /** Проверяет исчезновение столбца; чётные события идут слева, нечётные справа. */
    fun isRemoved(column: Int, elapsedMillis: Long, completedClears: Int): Boolean {
        val count = (elapsedMillis.coerceIn(0L, TOTAL_MILLIS) / STEP_MILLIS).toInt()
        return if (completedClears % 2 == 0) column < count else column >= 10 - count
    }
}


