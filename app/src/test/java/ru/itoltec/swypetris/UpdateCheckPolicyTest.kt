package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    private val now = 1_700_000_000_000L

    private fun limit(status: Int = 403, remaining: String? = null, retry: String? = null,
        reset: String? = null, message: String = "", at: Long = now) =
        githubRateLimit(status, remaining, retry, reset, message, at)

    @Test fun automaticIntervalHasExactBoundariesAndSafeClockArithmetic() {
        assertTrue(automaticCheckDue(0, null))
        assertFalse(automaticCheckDue(0, 0))
        assertFalse(automaticCheckDue(now + AUTO_CHECK_INTERVAL_MS - 1, now))
        assertTrue(automaticCheckDue(now + AUTO_CHECK_INTERVAL_MS, now))
        assertFalse(automaticCheckDue(now - 1, now))
        assertTrue(automaticCheckDue(Long.MAX_VALUE, 0))
        assertFalse(automaticCheckDue(Long.MIN_VALUE, Long.MIN_VALUE))
        assertTrue(automaticCheckDue(Long.MIN_VALUE + AUTO_CHECK_INTERVAL_MS, Long.MIN_VALUE))
    }

    @Test fun genericForbiddenAndOtherErrorsAreNotRateLimits() {
        assertNull(limit(message = "Resource not accessible by integration"))
        assertNull(limit(remaining = "42", reset = "1700003600", message = "Forbidden"))
        assertNull(limit(status = 401, remaining = "0"))
        assertNull(limit(status = 503, retry = "120"))
    }

    @Test fun primaryAndSecondaryEvidenceAreClassified() {
        assertEquals(now + 60_000, limit(status = 429)!!.retryAtMillis)
        assertEquals(now + 120_000, limit(remaining = " 0 ", reset = "1700000120")!!.retryAtMillis)
        assertEquals(now + 60_000, limit(message = "API RATE LIMIT EXCEEDED for this IP")!!.retryAtMillis)
        assertEquals(now + 60_000, limit(message = "You have exceeded a secondary rate limit.")!!.retryAtMillis)
        assertEquals(now + 60_000, limit(message = "You have triggered an abuse detection mechanism.")!!.retryAtMillis)
    }

    @Test fun retryAfterAndPrimaryResetBothRestrictTheDeadline() {
        assertEquals(now + 90_000, limit(retry = "90")!!.retryAtMillis)
        assertEquals(now + 120_000, limit(remaining = "0", retry = "90", reset = "1700000120")!!.retryAtMillis)
        // A secondary restriction must not inherit the unrelated primary-quota reset.
        assertEquals(now + 60_000, limit(status = 429, remaining = "42", reset = "1700003600")!!.retryAtMillis)
    }

    @Test fun httpDateFormsRetainTheirActualUtcDeadline() {
        for (date in listOf("Tue, 14 Nov 2023 22:15:20 GMT", "Tuesday, 14-Nov-23 22:15:20 GMT",
            "Tue Nov 14 22:15:20 2023")) {
            assertEquals(date, now + 120_000, limit(retry = date)!!.retryAtMillis)
        }
        assertEquals(now - 60_000, limit(retry = "Tue, 14 Nov 2023 22:12:20 GMT")!!.retryAtMillis)
        assertEquals(now, limit(retry = "0")!!.retryAtMillis)
    }

    @Test fun missingMalformedNegativeAndOverflowingTimingFallsBackSafely() {
        for (header in listOf("bad", "-1", Long.MAX_VALUE.toString(), "999999999999999999999",
            "Tue, 99 Nov 2023 22:15:20 GMT", "Tue, 14 Nov 2023 22:15:20 GMT garbage")) {
            assertEquals(header, now + 60_000, limit(status = 429, retry = header)!!.retryAtMillis)
        }
        assertEquals(now + 60_000, limit(remaining = "0", reset = Long.MAX_VALUE.toString())!!.retryAtMillis)
        assertEquals(Long.MAX_VALUE, limit(status = 429, retry = "120", at = Long.MAX_VALUE - 1)!!.retryAtMillis)
    }
}
