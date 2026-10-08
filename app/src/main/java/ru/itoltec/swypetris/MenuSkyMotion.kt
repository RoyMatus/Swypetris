package ru.itoltec.swypetris

import kotlin.math.PI
import kotlin.math.sin

private const val CLOUD_GAP = 80.0
private const val CLOUD_BASE_PERIOD_SECONDS = 32.0
private const val TWINKLE_BASE = .05
private const val TWINKLE_RANGE = .65
private const val TWINKLE_PERIOD_SECONDS = 2.7
private const val TWINKLE_PERIOD_GROUPS = 6
private const val TWINKLE_PERIOD_STAGGER = .39
private const val TWINKLE_PHASE_STAGGER = 1.83
private const val TOWER_BASE_BRIGHTNESS = .70
private const val TOWER_PULSE_BRIGHTNESS = .30
private const val TOWER_PERIOD_SECONDS = 4.2


/** Artwork-space timing from the approved #176 animation (09c2436). */
internal object MenuSkyMotion {
    const val WIDTH = 941f
    const val HEIGHT = 1671f
    data class Cloud(val start: Float, val y: Float, val width: Float, val speed: Double) {
        val period: Double get() = WIDTH + width + CLOUD_GAP
        fun x(seconds: Double): Float = ((
            start + seconds / CLOUD_BASE_PERIOD_SECONDS * period * speed) % period - width).toFloat()
        val loopSeconds: Double get() = CLOUD_BASE_PERIOD_SECONDS / speed
    }
    val clouds = listOf(Cloud(15f, 166f, 330f, 1.0), Cloud(755f, 206f, 285f, .82),
        Cloud(70f, 342f, 175f, .70), Cloud(760f, 420f, 410f, .61))
    fun twinkle(seconds: Double, index: Int): Float =
        (TWINKLE_BASE + TWINKLE_RANGE * (sin(seconds * 2 * PI / (TWINKLE_PERIOD_SECONDS + (
            index % TWINKLE_PERIOD_GROUPS) * TWINKLE_PERIOD_STAGGER) + index * TWINKLE_PHASE_STAGGER) + 1) / 2)
            .toFloat()
    fun towerPulse(seconds: Double): Float = (TOWER_BASE_BRIGHTNESS + TOWER_PULSE_BRIGHTNESS * sin(
        seconds * 2 * PI / TOWER_PERIOD_SECONDS)).toFloat()
}
