package ru.itoltec.swypetris

import java.net.HttpURLConnection
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
private const val SECONDARY_LIMIT_DELAY_MS = 60_000L
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MILLIS_PER_SECOND = 1000L

/** Future attempt timestamps are rebased by the preference owner after a clock rollback. */
internal fun automaticCheckDue(now: Long, lastAttempt: Long?): Boolean = lastAttempt == null ||
    (now >= Long.MIN_VALUE + AUTO_CHECK_INTERVAL_MS && lastAttempt <= now - AUTO_CHECK_INTERVAL_MS)

/** A generic access-denied response is not evidence that a GitHub rate limit was exceeded. */
internal fun githubRateLimit(status: Int, remaining: String?, retryAfter: String?, reset: String?,
    message: String, now: Long): UpdateRateLimitException? {
    val text = message.lowercase(Locale.ROOT)
    val primary = remaining?.trim() == "0" || text.contains("api rate limit exceeded")
    val secondary = text.contains("secondary rate limit") || text.contains("abuse detection mechanism")
    val limited = primary || secondary || !retryAfter.isNullOrBlank()
    if (status != HTTP_TOO_MANY_REQUESTS && !(status == HttpURLConnection.HTTP_FORBIDDEN && limited)) return null
    val deadlines = listOfNotNull(retryDeadline(retryAfter, now),
        if (primary) reset?.trim()?.toLongOrNull()?.let { secondsDeadline(0, it) } else null)
    val deadline = deadlines.maxOrNull() ?: if (now > Long.MAX_VALUE - SECONDARY_LIMIT_DELAY_MS)
        Long.MAX_VALUE else now + SECONDARY_LIMIT_DELAY_MS
    return UpdateRateLimitException(deadline)
}

private fun secondsDeadline(base: Long, seconds: Long): Long? {
    if (seconds < 0 || seconds > Long.MAX_VALUE / MILLIS_PER_SECOND) return null
    val delay = seconds * MILLIS_PER_SECOND
    return if (base > Long.MAX_VALUE - delay) null else base + delay
}

/** Retry-After permits delta seconds or an HTTP date, including its two obsolete date forms. */
private fun retryDeadline(header: String?, now: Long): Long? {
    val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val seconds = value.toLongOrNull()
    return if (seconds != null) secondsDeadline(now, seconds) else httpDateDeadline(value, now)
}

private fun httpDateDeadline(value: String, now: Long): Long? {
    val zone = TimeZone.getTimeZone("GMT")
    val centuryStart = Calendar.getInstance(zone, Locale.US).apply {
        timeInMillis = now
        add(Calendar.YEAR, -50)
    }.time
    for (pattern in listOf("EEE, dd MMM yyyy HH:mm:ss 'GMT'", "EEEE, dd-MMM-yy HH:mm:ss 'GMT'",
        "EEE MMM d HH:mm:ss yyyy")) {
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = zone
            isLenient = false
            set2DigitYearStart(centuryStart)
        }
        val position = ParsePosition(0)
        val date = format.parse(value, position)
        if (date != null && position.index == value.length) return date.time
    }
    return null
}
