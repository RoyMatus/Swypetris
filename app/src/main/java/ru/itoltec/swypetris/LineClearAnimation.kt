package ru.itoltec.swypetris

/** Sequentially removes ten cells at 60 ms intervals. */
object LineClearAnimation {
    const val STEP_MILLIS = 60L
    const val TOTAL_MILLIS = 600L

    /** Checks whether a column has vanished; even steps start left, odd steps start right. */
    fun isRemoved(column: Int, elapsedMillis: Long, completedClears: Int): Boolean {
        val count = (elapsedMillis.coerceIn(0L, TOTAL_MILLIS) / STEP_MILLIS).toInt()
        return if (completedClears % 2 == 0) column < count else column >= 10 - count
    }
}


