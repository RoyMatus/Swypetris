package ru.itoltec.swypetris

/** Pulse parameters with a shorter duration for motors that cannot control amplitude. */
internal data class HapticPulse(val duration: Long, val amplitude: Int, val fallbackDuration: Long = duration) {
    /** A weak drop stays short on motors with fixed vibration strength. */
    fun durationFor(amplitudeControl: Boolean): Long = if (amplitudeControl) duration else fallbackDuration

    /** Keep event timing on amplitude-capable motors; shorten the fallback pulse otherwise. */
    fun softened(): HapticPulse = copy(amplitude = ((amplitude + 1) / 2).coerceAtLeast(1),
        fallbackDuration = (fallbackDuration / 2 + fallbackDuration % 2).coerceAtLeast(1))

    companion object {
        val HoldReady = HapticPulse(35L, 100, 20L)
        val Drop = HapticPulse(70L, 128, 35L)
        val Clear = clear(LineClearAnimation.TOTAL_MILLIS)
        val Preview = HapticPulse(100L, 220)

        /** Keeps a clear synchronized on amplitude-capable motors and shortens fixed-strength fallback. */
        fun clear(remainingMillis: Long) = HapticPulse(remainingMillis, amplitude = 127,
            fallbackDuration = (remainingMillis + 1) / 2)
    }
}
