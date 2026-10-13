package ru.itoltec.swypetris

import java.net.HttpURLConnection
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal const val AUTO_CHECK_INTERVAL_MS = 60L * 60 * 1000
internal const val MANUAL_CHECK_LIMIT = 59
internal const val MANUAL_CHECK_WINDOW_MS = 60L * 60 * 1000
private const val SECONDARY_LIMIT_DELAY_MS = 60_000L
private const val MAX_BACKOFF_MS = 60L * 60 * 1000
private const val MAX_BACKOFF_SHIFT = 6
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MILLIS_PER_SECOND = 1000L

/** Keep the last durable time on rollback; elapsed time handles wall-clock jumps within one boot. */
internal fun policyTime(wall: Long, elapsed: Long, anchorWall: Long?, anchorElapsed: Long?): Long {
    return when {
        anchorWall == null || anchorElapsed == null -> wall
        elapsed < anchorElapsed -> anchorWall // reboot: conservatively restart the wait
        else -> saturatingAdd(anchorWall, elapsed - anchorElapsed)
    }
}

internal fun saturatingAdd(base: Long, positive: Long): Long =
    if (positive > 0 && base > Long.MAX_VALUE - positive) Long.MAX_VALUE else base + positive

internal data class ManualEligibility(val attempts: List<Long>, val retryAt: Long?)

/** Count actual starts in a rolling hour; future entries stay counted after clock rollback. */
internal fun manualEligibility(now: Long, attempts: List<Long>): ManualEligibility {
    val current = if (now < Long.MIN_VALUE + MANUAL_CHECK_WINDOW_MS) attempts
        else attempts.filter { it > now - MANUAL_CHECK_WINDOW_MS }
    val retryAt = if (current.size >= MANUAL_CHECK_LIMIT)
        saturatingAdd(current.min(), MANUAL_CHECK_WINDOW_MS) else null
    return ManualEligibility(current, retryAt)
}

internal fun parseManualAttempts(value: String?): List<Long>? {
    val parts = value?.takeIf { it.isNotEmpty() }?.split(',') ?: emptyList()
    val values = parts.mapNotNull(String::toLongOrNull)
    return values.takeIf { it.size == parts.size &&
        it.size <= MANUAL_CHECK_LIMIT && it.zipWithNext().all { (a, b) -> a <= b } }
}

/** A confirmed secondary limit with no server deadline backs off, without an automatic retry loop. */
internal fun secondaryBackoffMillis(previousFailures: Int): Long =
    (SECONDARY_LIMIT_DELAY_MS shl previousFailures.coerceIn(0, MAX_BACKOFF_SHIFT))
        .coerceAtMost(MAX_BACKOFF_MS)

/** A generic store failure has no documented server deadline in the SDK. */
internal fun storeBackoffMillis(previousFailures: Int): Long = secondaryBackoffMillis(previousFailures)

/** A future attempt stays due only after a full interval, including across process restarts. */
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
    return UpdateRateLimitException(deadline, secondary && deadlines.isEmpty())
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
