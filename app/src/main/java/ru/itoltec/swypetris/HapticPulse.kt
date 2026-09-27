package ru.itoltec.swypetris

/** Параметры импульса и короткий вариант для мотора без управления амплитудой. */
internal data class HapticPulse(val duration: Long, val amplitude: Int, val fallbackDuration: Long = duration) {
    /** Слабый бросок остаётся коротким и на устройствах с фиксированной силой мотора. */
    fun durationFor(amplitudeControl: Boolean): Long = if (amplitudeControl) duration else fallbackDuration

    /** Keep event timing on amplitude-capable motors; shorten the fallback pulse otherwise. */
    fun softened(): HapticPulse = copy(amplitude = ((amplitude + 1) / 2).coerceAtLeast(1),
        fallbackDuration = (fallbackDuration / 2 + fallbackDuration % 2).coerceAtLeast(1))

    companion object {
        val Drop = HapticPulse(70L, 128, 35L)
        val Clear = HapticPulse(LineClearAnimation.TOTAL_MILLIS, 255)
        val Preview = HapticPulse(100L, 220)
    }
}
