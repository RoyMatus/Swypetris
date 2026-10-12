package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private const val SPARK_PIXEL_DP = 2
private const val ROCKET_TRAIL = 9
private const val UPPER_BURST_SCALE = .65f
private const val UPPER_BURST_PARTICLES = 20
private const val MAX_DISTANCE_HEIGHT = .45f
private const val TRAIL_ALPHA = .65f
private const val FLASH_CELLS = 3
private const val MIN_PIXEL_COLUMNS = 140
private const val SPARK_CLEARANCE_DP = 20
private val WARM_SPARKS = listOf(Color(0xFFFFD44A), Color(0xFFFF8A32), Color(0xFFFFE875))
private val COOL_SPARKS = listOf(Color(0xFF62EEFF), Color(0xFF249AFF), Color(0xFFCDF8FF))
private val UPPER_SPARKS = listOf(Color(0xFFFFE875), Color(0xFFFFD44A), Color(0xFFFF8A32))
private val ABSOLUTE_CENTERS = listOf(.18f, .82f, .5f)
private val SPARK_HIGHLIGHT = Color(0xFFFFF5DA)

internal enum class VictoryCelebration { ROUND, ABSOLUTE, POSTCARD }

/** Detached pixel sparks follow shared trajectories behind the protected foreground. */
@Composable
internal fun VictoryFireworks(celebration: VictoryCelebration = VictoryCelebration.ROUND, time: () -> Long) {
    Canvas(Modifier.fillMaxSize().testTag("victoryFireworks")) {
        drawVictoryFireworks(time(), celebration)
    }
}

internal fun DrawScope.drawVictoryFireworks(elapsed: Long, celebration: VictoryCelebration,
    centers: List<Offset>? = null) {
    val absolute = celebration != VictoryCelebration.ROUND
    repeat(if (absolute) ABSOLUTE_CENTERS.size else 2) { burst ->
        val age = VictoryMotion.age(elapsed, burst)
        val center = centers?.get(burst) ?: Offset(size.width *
            if (absolute) ABSOLUTE_CENTERS[burst] else if (burst == 0) .23f else .77f,
            size.height * if (absolute && burst == 2) .075f else .13f)
        val colors = when (burst) { 0 -> WARM_SPARKS; 1 -> COOL_SPARKS; else -> UPPER_SPARKS }
        if (age < VictoryMotion.LAUNCH_FRACTION) drawRocket(center, age, burst, colors)
        else drawBurst(center, VictoryMotion.burstSeconds(age), burst, colors,
            if (absolute && burst == 2) UPPER_BURST_SCALE else 1f,
            if (centers == null) size.height * VictoryLayout.forHeight(size.height.toDp()).contentTop else size.height)
    }
}

private fun DrawScope.drawRocket(center: Offset, age: Float, burst: Int, colors: List<Color>) {
    val launch = VictoryMotion.launch(age)
    val start = center + Offset(size.width * if (burst == 0) -.18f else .18f, size.height * .29f)
    val alpha = VictoryMotion.opacity(launch)
    repeat(ROCKET_TRAIL) { spark ->
        val progress = (launch - spark * .035f).coerceAtLeast(0f)
        val point = start + (center - start) * progress
        drawSparkPixel(point, colors[spark % colors.size],
            alpha * (1 - spark / ROCKET_TRAIL.toFloat()), sparkPixel())
    }
}

private fun DrawScope.drawBurst(center: Offset, seconds: Float, burst: Int, colors: List<Color>,
    scale: Float, bottom: Float) {
    val pixel = sparkPixel()
    val distanceScale = min(size.width, size.height * MAX_DISTANCE_HEIGHT) * scale
    val count = if (scale < 1f) UPPER_BURST_PARTICLES else FireworkParticles.COUNT
    repeat(count) { index ->
        val particle = FireworkParticles.spark(burst, index * FireworkParticles.COUNT / count)
        val head = center + FireworkParticles.position(particle, seconds) * distanceScale
        val clearance = ((bottom - head.y - pixel * 2) / SPARK_CLEARANCE_DP.dp.toPx()).coerceIn(0f, 1f)
        val alpha = FireworkParticles.opacity(particle, seconds) * clearance
        if (alpha > 0f) {
            for (sample in particle.trailSamples - 1 downTo 1) {
                val earlier = FireworkParticles.trailTime(seconds, sample)
                if (earlier >= 0f) drawSparkPixel(
                    center + FireworkParticles.position(particle, earlier) * distanceScale,
                    colors[index % colors.size],
                    alpha * (1 - sample / particle.trailSamples.toFloat()) * TRAIL_ALPHA, pixel)
            }
            drawSparkPixel(head, colors[index % colors.size], alpha, pixel, 2)
            drawSparkPixel(head, SPARK_HIGHLIGHT, alpha, pixel)
        }
    }
    if (seconds < FireworkParticles.FLASH_SECONDS) {
        val alpha = 1 - seconds / FireworkParticles.FLASH_SECONDS
        drawSparkPixel(center - Offset(pixel, pixel), colors.first(), alpha, pixel, FLASH_CELLS)
        drawSparkPixel(center, SPARK_HIGHLIGHT, alpha, pixel)
    }
}

private fun DrawScope.sparkPixel(): Float =
    SPARK_PIXEL_DP.dp.toPx().coerceAtMost(size.width / MIN_PIXEL_COLUMNS).roundToInt().coerceAtLeast(1).toFloat()

/** Integer cells keep heads and broken historical trails crisp at any density. */
private fun DrawScope.drawSparkPixel(point: Offset, color: Color, alpha: Float, pixel: Float, cells: Int = 1) {
    val snapped = Offset(floor(point.x / pixel) * pixel, floor(point.y / pixel) * pixel)
    drawRect(color.copy(alpha = alpha), snapped, Size(pixel * cells, pixel * cells))
}
