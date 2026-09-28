package ru.itoltec.swypetris

import kotlin.math.sin

/** Deterministic intro paths use normalized coordinates and do not depend on frame rate. */
internal object LaunchIntroMotion {
    const val DURATION = 3000L

    /** The shared Red Square artwork approaches its final menu framing before controls appear. */
    fun approachProgress(elapsed: Long): Float = easeOut(progress(elapsed, 0L, 2000L))

    /** The dark-theme wordmark resolves as alternating scanline bands after the approach. */
    fun wordmarkReveal(elapsed: Long): Float = easeOut(progress(elapsed, 1550L, 1000L))

    /** Stripes arrive in sequence from opposite sides and finish assembly after 1100 ms. */
    fun stripeProgress(elapsed: Long, index: Int, count: Int): Float {
        val delay = if (count <= 1) 0L else 350L * index / (count - 1)
        return easeOut(progress(elapsed, delay, 750L))
    }

    /** Cube fall order is shuffled but stays stable across screen recreation. */
    fun cubeProgress(elapsed: Long, index: Int): Float =
        progress(elapsed, 450L + seed(index, 17) % 1301, 700L + seed(index, 71) % 201)

    /** Moves horizontal position and tilt smoothly toward each cube's final location. */
    fun easeOut(value: Float): Float = 1f - (1f - value) * (1f - value) * (1f - value)

    /** After accelerated descent, a cube bounces slightly and settles exactly in place. */
    fun fallProgress(value: Float): Float = if (value < .82f) (value / .82f) * (value / .82f)
        else 1f - .016f * sin((value - .82f) / .18f * Math.PI).toFloat()

    /** Buttons appear only after the wordmark finishes assembling. */
    fun buttonsAlpha(elapsed: Long): Float = progress(elapsed, 2700L, 300L)

    /** Stable index shuffling without shared mutable Random state. */
    fun seed(index: Int, salt: Int): Int = ((index.toLong() * 1103515245L + salt * 12345L) xor
        (index.toLong() * 2654435761L ushr 9)).and(0x7fffffffL).toInt()

    /** Clamps elapsed time within a single animation stage. */
    private fun progress(elapsed: Long, delay: Long, duration: Long): Float =
        ((elapsed - delay).toFloat() / duration).coerceIn(0f, 1f)
}
