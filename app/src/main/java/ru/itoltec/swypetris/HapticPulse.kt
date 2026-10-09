package ru.itoltec.swypetris

/** Upper bound of the user's global intensity, preserving the designed effect at 100%. */
internal const val MAX_VIBRATION_STRENGTH = 100

/** Per-event timing and amplitude before applying the user intensity. */
internal data class HapticPulse(val duration: Long, val amplitude: Int, val fallbackDuration: Long = duration) {
    /** A weak drop stays short on motors with fixed vibration strength. */
    fun durationFor(amplitudeControl: Boolean): Long = if (amplitudeControl) duration else fallbackDuration

    /** Keep event timing on amplitude-capable motors; shorten the fallback pulse otherwise. */
    fun softened(): HapticPulse = copy(amplitude = ((amplitude + 1) / 2).coerceAtLeast(1),
        fallbackDuration = (fallbackDuration / 2 + fallbackDuration % 2).coerceAtLeast(1))

    /** Preserve timing with amplitude control; fixed-strength motors use shorter pulses. */
    fun scaled(percent: Int): HapticPulse? {
        require(percent in 0..MAX_VIBRATION_STRENGTH)
        if (percent == 0 || duration <= 0) return null
        return copy(amplitude = ((amplitude * percent + MAX_VIBRATION_STRENGTH / 2) /
            MAX_VIBRATION_STRENGTH).coerceAtLeast(1),
            fallbackDuration = (fallbackDuration * percent / MAX_VIBRATION_STRENGTH).coerceAtLeast(1))
    }

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
