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
private const val SPARKS_PER_RAY = 5
private const val ROCKET_TRAIL = 9
private val WARM_SPARKS = listOf(Color(0xFFFFD44A), Color(0xFFFF8A32), Color(0xFFFF4267))
private val COOL_SPARKS = listOf(Color(0xFF62EEFF), Color(0xFF249AFF), Color(0xFFFFD44A))

/** Approved orange/blue launches, expanding trails and falling embers, behind the fixed foreground. */
@Composable
internal fun VictoryFireworks(time: () -> Long) {
    Canvas(Modifier.fillMaxSize().testTag("victoryFireworks")) {
        val elapsed = time()
        repeat(2) { burst ->
            val age = VictoryMotion.age(elapsed, burst)
            val center = Offset(size.width * if (burst == 0) .23f else .77f, size.height * .13f)
            val colors = if (burst == 0) WARM_SPARKS else COOL_SPARKS
            if (age < VictoryMotion.LAUNCH_FRACTION) drawRocket(center, age, burst, colors)
            else drawBurst(center, VictoryMotion.expansion(age), colors)
        }
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

private fun DrawScope.drawBurst(center: Offset, progress: Float, colors: List<Color>) {
    val growth = (progress / .45f).coerceIn(0f, 1f)
    val radius = size.width * .28f * (1 - (1 - growth) * (1 - growth))
    val alpha = VictoryMotion.opacity(progress)
    val pixel = 2.dp.toPx()
    repeat(FIREWORK_RAYS) { ray ->
        val angle = ray * 2.0 * PI / FIREWORK_RAYS
        repeat(SPARKS_PER_RAY) { spark ->
            val distance = radius * (1 - spark * .09f) * if (ray % 2 == 0) 1f else .83f
            val point = center + Offset(cos(angle).toFloat() * distance,
                sin(angle).toFloat() * distance + size.width * .14f * progress * progress)
            drawRect(colors[(ray + spark) % colors.size].copy(alpha = alpha * (1 - spark * .12f)),
                point, Size(pixel, pixel))
        }
    }
    drawRect(colors.first().copy(alpha = alpha * (1 - progress)),
        center - Offset(pixel, pixel), Size(pixel * 2, pixel * 2))
}
