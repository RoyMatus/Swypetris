package ru.itoltec.swypetris

private const val THOUSAND = 1000L
private const val MILLION = THOUSAND * THOUSAND
private const val BILLION = MILLION * THOUSAND
private const val TRILLION = BILLION * THOUSAND
private const val DECIMAL_LIMIT = 100
private const val DECIMAL_BASE = 10

/** Truncate compact labels without rounding an unearned award up; accessibility keeps the exact count. */
internal fun fruitQuantityLabel(count: Long): String {
    val (divisor, suffix) = when {
        count >= TRILLION -> TRILLION to "T"
        count >= BILLION -> BILLION to "B"
        count >= MILLION -> MILLION to "M"
        count >= THOUSAND -> THOUSAND to "K"
        else -> return count.toString()
    }
    val whole = count / divisor
    val tenth = (count % divisor) / (divisor / DECIMAL_BASE)
    return if (whole < DECIMAL_LIMIT && tenth > 0) "$whole.$tenth$suffix" else "$whole$suffix"
}
