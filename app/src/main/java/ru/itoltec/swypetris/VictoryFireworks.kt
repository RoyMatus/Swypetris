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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val FIREWORK_RAYS = 24
private const val SPARKS_PER_RAY = 9
private const val SPARK_PIXEL_DP = 3
private const val ROCKET_TRAIL = 9
private val WARM_SPARKS = listOf(Color(0xFFFFD44A), Color(0xFFFF8A32), Color(0xFFFF4267))
private val COOL_SPARKS = listOf(Color(0xFF62EEFF), Color(0xFF249AFF), Color(0xFFFFD44A))
private val PINK_SPARKS = listOf(Color(0xFFFFE875), Color(0xFFFF428A), Color(0xFFFF2262))
private const val ABSOLUTE_BURST_RADIUS = .19f
private const val ROUND_BURST_RADIUS = .28f
private val ABSOLUTE_CENTERS = listOf(.18f, .5f, .82f)

internal enum class VictoryCelebration { ROUND, ABSOLUTE, POSTCARD }

/** Approved orange/blue launches, expanding trails and falling embers, behind the fixed foreground. */
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
            if (absolute) ABSOLUTE_CENTERS[burst] else if (burst == 0) .23f else .77f, size.height * .13f)
        val colors = when (burst) { 0 -> WARM_SPARKS; 1 -> COOL_SPARKS; else -> PINK_SPARKS }
        if (age < VictoryMotion.LAUNCH_FRACTION) drawRocket(center, age, burst, colors)
        else drawBurst(center, VictoryMotion.expansion(age), colors,
            if (absolute) ABSOLUTE_BURST_RADIUS else ROUND_BURST_RADIUS)
    }
}

private fun DrawScope.drawRocket(center: Offset, age: Float, burst: Int, colors: List<Color>) {
    val launch = VictoryMotion.launch(age)
    val start = center + Offset(size.width * if (burst == 0) -.18f else .18f, size.height * .29f)
    val alpha = VictoryMotion.opacity(launch)
    repeat(ROCKET_TRAIL) { spark ->
        val progress = (launch - spark * .035f).coerceAtLeast(0f)
        val point = start + (center - start) * progress
        drawRect(colors[spark % colors.size].copy(alpha = alpha * (1 - spark / ROCKET_TRAIL.toFloat())),
            point, Size(2.dp.toPx(), 4.dp.toPx()))
    }
}

private fun DrawScope.drawBurst(center: Offset, progress: Float, colors: List<Color>, radiusFraction: Float) {
    val growth = (progress / .45f).coerceIn(0f, 1f)
    val radius = size.width * radiusFraction * (1 - (1 - growth) * (1 - growth))
    val alpha = VictoryMotion.opacity(progress)
    val pixel = SPARK_PIXEL_DP.dp.toPx()
    repeat(FIREWORK_RAYS) { ray ->
        val angle = ray * 2.0 * PI / FIREWORK_RAYS
        var previous: Offset? = null
        repeat(SPARKS_PER_RAY) { spark ->
            val distance = radius * (1 - spark * .09f) * if (ray % 2 == 0) 1f else .83f
            val trailAngle = angle + spark * .035f * if (ray % 2 == 0) 1 else -1
            val point = center + Offset(cos(trailAngle).toFloat() * distance,
                sin(trailAngle).toFloat() * distance + size.width * .14f * progress * progress)
            previous?.let { outer ->
                drawLine(colors[(ray + spark) % colors.size].copy(alpha = alpha * (1 - progress)),
                    outer, point, strokeWidth = pixel * .6f)
            }
            previous = point
            drawRect(colors[(ray + spark) % colors.size].copy(alpha = alpha * (1 - spark * .12f)),
                point, Size(pixel, pixel))
        }
    }
    drawRect(colors.first().copy(alpha = alpha * (1 - progress)),
        center - Offset(pixel, pixel), Size(pixel * 2, pixel * 2))
}
