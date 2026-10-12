package ru.itoltec.swypetris

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import androidx.compose.ui.geometry.Offset

/** Periodic timing keeps every launch and fading burst continuous at the loop boundary. */
internal object VictoryMotion {
    const val PERIOD_MILLIS = 4200L
    const val STAGGER_MILLIS = 650L
    const val LAUNCH_FRACTION = .18f
    private const val MILLIS_PER_SECOND = 1000f

    fun advance(phase: Long, delta: Long): Long =
        (phase.coerceAtLeast(0) % PERIOD_MILLIS + delta.coerceAtLeast(0) % PERIOD_MILLIS) % PERIOD_MILLIS

    fun age(phase: Long, burst: Int): Float =
        Math.floorMod(phase % PERIOD_MILLIS - burst * STAGGER_MILLIS, PERIOD_MILLIS).toFloat() / PERIOD_MILLIS

    fun launch(age: Float): Float = (age / LAUNCH_FRACTION).coerceIn(0f, 1f)

    fun expansion(age: Float): Float = ((age - LAUNCH_FRACTION) / (1f - LAUNCH_FRACTION)).coerceIn(0f, 1f)

    fun burstSeconds(age: Float): Float = expansion(age) *
        (PERIOD_MILLIS * (1 - LAUNCH_FRACTION)) / MILLIS_PER_SECOND

    fun opacity(progress: Float): Float = when {
        progress <= 0f || progress >= 1f -> 0f
        else -> sin(progress * PI).toFloat()
    }
}

/** Stable particles and time-sampled trajectories, shared by every celebration and the postcard. */
internal object FireworkParticles {
    const val COUNT = 32
    const val TRAIL_SAMPLES = 10
    const val TRAIL_STEP_SECONDS = .05f
    const val FLASH_SECONDS = .09f
    private const val GRAVITY = .65f
    private const val FADE_SECONDS = .7f
    private const val SEED_MULTIPLIER = 0x45d9f3b
    private const val SALT_MULTIPLIER = 0x119de1f3
    private const val MIX_SHIFT = 16
    private const val FRACTION_SHIFT = 8
    private const val FRACTION_RANGE = 16777216f

    data class Spark(val velocity: Offset, val drag: Float, val lifetime: Float, val trailSamples: Int)

    private val bursts = Array(3) { burst ->
        Array(COUNT) { index ->
            val seed = burst * COUNT + index
            val angle = (index + noise(seed, 1) * .7f) * 2.0 * PI / COUNT
            val speed = .24f + noise(seed, 2) * .5f
            val velocity = Offset(cos(angle).toFloat() * speed, sin(angle).toFloat() * speed)
            Spark(velocity, 1.4f + noise(seed, 5) * 1.5f,
                2f + noise(seed, 3) * .5f - max(velocity.y, 0f) * .6f,
                6 + (noise(seed, 4) * 5).toInt())
        }
    }

    fun spark(burst: Int, index: Int): Spark = bursts[burst][index]

    /** Pure elapsed-time evaluation avoids frame integration drift and per-frame particle allocation. */
    fun position(spark: Spark, seconds: Float): Offset {
        val time = seconds.coerceAtLeast(0f)
        val travel = (1f - exp(-spark.drag * time)) / spark.drag
        return Offset(spark.velocity.x * travel,
            spark.velocity.y * travel + GRAVITY / spark.drag * (time - travel))
    }

    fun opacity(spark: Spark, seconds: Float): Float =
        if (seconds < 0f || seconds >= spark.lifetime) 0f
        else ((spark.lifetime - seconds) / FADE_SECONDS).coerceIn(0f, 1f)

    /** Each trailing sample is an earlier point of the head's own trajectory, never a radial spoke. */
    fun trailTime(seconds: Float, sample: Int): Float = seconds - sample * TRAIL_STEP_SECONDS

    private fun noise(seed: Int, salt: Int): Float {
        var bits = seed * SEED_MULTIPLIER + salt * SALT_MULTIPLIER
        bits = (bits xor (bits ushr MIX_SHIFT)) * SEED_MULTIPLIER
        return (bits xor (bits ushr MIX_SHIFT)).ushr(FRACTION_SHIFT) / FRACTION_RANGE
    }
}
