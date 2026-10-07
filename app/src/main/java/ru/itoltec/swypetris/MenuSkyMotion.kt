package ru.itoltec.swypetris

import kotlin.math.PI
import kotlin.math.sin

/** Artwork-space timing from the approved #176 animation (09c2436). */
internal object MenuSkyMotion {
    const val WIDTH = 941f
    const val HEIGHT = 1671f
    data class Cloud(val start: Float, val y: Float, val width: Float, val speed: Double) {
        val period: Double get() = WIDTH + width + 80.0
        fun x(seconds: Double): Float = ((start + seconds / 32.0 * period * speed) % period - width).toFloat()
        val loopSeconds: Double get() = 32.0 / speed
    }
    val clouds = listOf(Cloud(15f, 166f, 330f, 1.0), Cloud(755f, 206f, 285f, .82),
        Cloud(70f, 342f, 175f, .70), Cloud(760f, 420f, 410f, .61))
    fun twinkle(seconds: Double, index: Int): Float =
        (.05 + .65 * (sin(seconds * 2 * PI / (2.7 + (index % 6) * .39) + index * 1.83) + 1) / 2).toFloat()
    fun towerPulse(seconds: Double): Float = (.70 + .30 * sin(seconds * 2 * PI / 4.2)).toFloat()
}
