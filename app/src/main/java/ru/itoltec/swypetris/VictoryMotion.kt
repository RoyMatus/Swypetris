package ru.itoltec.swypetris

import kotlin.math.PI
import kotlin.math.sin

/** Periodic timing keeps every launch and fading burst continuous at the loop boundary. */
internal object VictoryMotion {
    const val PERIOD_MILLIS = 4200L
    const val STAGGER_MILLIS = 650L
    const val LAUNCH_FRACTION = .18f

    fun advance(phase: Long, delta: Long): Long =
        (phase.coerceAtLeast(0) % PERIOD_MILLIS + delta.coerceAtLeast(0) % PERIOD_MILLIS) % PERIOD_MILLIS

    fun age(phase: Long, burst: Int): Float =
        Math.floorMod(phase % PERIOD_MILLIS - burst * STAGGER_MILLIS, PERIOD_MILLIS).toFloat() / PERIOD_MILLIS

    fun launch(age: Float): Float = (age / LAUNCH_FRACTION).coerceIn(0f, 1f)

    fun expansion(age: Float): Float = ((age - LAUNCH_FRACTION) / (1f - LAUNCH_FRACTION)).coerceIn(0f, 1f)

    fun opacity(progress: Float): Float = when {
        progress <= 0f || progress >= 1f -> 0f
        else -> sin(progress * PI).toFloat()
    }
}
