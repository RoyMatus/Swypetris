package ru.itoltec.swypetris

import kotlin.math.sin

private const val APPROACH_MILLIS = 2000L
private const val WORDMARK_DELAY_MILLIS = 1550L
private const val WORDMARK_REVEAL_MILLIS = 1000L
private const val STRIPE_STAGGER_MILLIS = 350L
private const val STRIPE_TRAVEL_MILLIS = 750L
private const val CUBE_DELAY_MILLIS = 450L
private const val CUBE_DELAY_SALT = 17
private const val CUBE_DELAY_SPREAD = 1301
private const val CUBE_TRAVEL_MILLIS = 700L
private const val CUBE_TRAVEL_SALT = 71
private const val CUBE_TRAVEL_SPREAD = 201
private const val FALL_ACCELERATION_PHASE = .82f
private const val BOUNCE_AMPLITUDE = .016f
private const val BOUNCE_PHASE = .18f
private const val BUTTON_DELAY_MILLIS = 2700L
private const val BUTTON_FADE_MILLIS = 300L
private const val SEED_MULTIPLIER = 1103515245L
private const val SALT_MULTIPLIER = 12345L
private const val SEED_MIX_MULTIPLIER = 2654435761L
private const val SEED_MIX_SHIFT = 9
private const val POSITIVE_SEED_MASK = 0x7fffffffL


/** Deterministic intro paths use normalized coordinates and do not depend on frame rate. */
internal object LaunchIntroMotion {
    const val DURATION = 3000L

    /** The shared Red Square artwork approaches its final menu framing before controls appear. */
    fun approachProgress(elapsed: Long): Float = easeOut(progress(elapsed, 0L, APPROACH_MILLIS))

    /** The dark-theme wordmark resolves as alternating scanline bands after the approach. */
    fun wordmarkReveal(elapsed: Long): Float = easeOut(progress(elapsed, WORDMARK_DELAY_MILLIS, WORDMARK_REVEAL_MILLIS))

    /** Stripes arrive in sequence from opposite sides and finish assembly after 1100 ms. */
    fun stripeProgress(elapsed: Long, index: Int, count: Int): Float {
        val delay = if (count <= 1) 0L else STRIPE_STAGGER_MILLIS * index / (count - 1)
        return easeOut(progress(elapsed, delay, STRIPE_TRAVEL_MILLIS))
    }

    /** Cube fall order is shuffled but stays stable across screen recreation. */
    fun cubeProgress(elapsed: Long, index: Int): Float =
        progress(elapsed, CUBE_DELAY_MILLIS + seed(index, CUBE_DELAY_SALT) % CUBE_DELAY_SPREAD,
            CUBE_TRAVEL_MILLIS + seed(index, CUBE_TRAVEL_SALT) % CUBE_TRAVEL_SPREAD)

    /** Moves horizontal position and tilt smoothly toward each cube's final location. */
    fun easeOut(value: Float): Float = 1f - (1f - value) * (1f - value) * (1f - value)

    /** After accelerated descent, a cube bounces slightly and settles exactly in place. */
    fun fallProgress(value: Float): Float = if (value < FALL_ACCELERATION_PHASE) (
        value / FALL_ACCELERATION_PHASE) * (value / FALL_ACCELERATION_PHASE)
        else 1f - BOUNCE_AMPLITUDE * sin((value - FALL_ACCELERATION_PHASE) / BOUNCE_PHASE * Math.PI).toFloat()

    /** Buttons appear only after the wordmark finishes assembling. */
    fun buttonsAlpha(elapsed: Long): Float = progress(elapsed, BUTTON_DELAY_MILLIS, BUTTON_FADE_MILLIS)

    /** Stable index shuffling without shared mutable Random state. */
    fun seed(index: Int, salt: Int): Int = ((index.toLong() * SEED_MULTIPLIER + salt * SALT_MULTIPLIER) xor
        (index.toLong() * SEED_MIX_MULTIPLIER ushr SEED_MIX_SHIFT)).and(POSITIVE_SEED_MASK).toInt()

    /** Clamps elapsed time within a single animation stage. */
    private fun progress(elapsed: Long, delay: Long, duration: Long): Float =
        ((elapsed - delay).toFloat() / duration).coerceIn(0f, 1f)
}
