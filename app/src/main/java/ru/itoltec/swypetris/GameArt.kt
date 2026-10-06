package ru.itoltec.swypetris

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.sin

private const val MENU_SKY_CYCLE_MILLIS = 36_000f

private data class SkyStar(val x: Float, val y: Float, val phase: Float, val radius: Float)
private val menuSkyStars = listOf(
    SkyStar(.08f, .10f, .05f, 1.1f), SkyStar(.15f, .17f, .42f, .8f),
    SkyStar(.24f, .08f, .73f, 1.0f), SkyStar(.33f, .20f, .22f, .7f),
    SkyStar(.43f, .12f, .58f, .9f), SkyStar(.56f, .08f, .84f, 1.1f),
    SkyStar(.68f, .18f, .36f, .8f), SkyStar(.78f, .10f, .66f, .9f),
    SkyStar(.88f, .21f, .14f, .7f), SkyStar(.94f, .12f, .91f, 1.0f),
    SkyStar(.12f, .28f, .61f, .7f), SkyStar(.29f, .30f, .31f, .8f),
    SkyStar(.72f, .29f, .78f, .8f), SkyStar(.90f, .32f, .49f, .7f)
)

/** The reference Red Square composition has matching night and day lighting. */
@Composable
internal fun ThemeBackdrop(palette: GamePalette, approach: Float = 1f) {
    val artwork = ImageBitmap.imageResource(
        if (palette.light) R.drawable.red_square_day else R.drawable.red_square_synthwave)
    val owner = LocalLifecycleOwner.current
    val animationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val phase = if (animationsEnabled && resumed) {
        val transition = rememberInfiniteTransition(label = "menuSky")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(MENU_SKY_CYCLE_MILLIS.toInt(), easing = LinearEasing)
            ),
            label = "menuSkyPhase"
        )
        animatedPhase
    } else 0f
    val approachLayer = Modifier.fillMaxSize().graphicsLayer {
        scaleX = 1f + .08f * approach
        scaleY = scaleX
        transformOrigin = TransformOrigin(.5f, .28f)
    }
    Box(Modifier.fillMaxSize()) {
        Image(artwork, contentDescription = null, modifier = approachLayer,
            contentScale = ContentScale.Crop,
            colorFilter = if (palette.light || palette.id == "classic" || palette.id == "synthwave_84")
                null else artworkColorFilter(palette))
        Canvas(approachLayer) {
            drawAnimatedSky(artwork, palette, phase, animationsEnabled)
        }
        Canvas(Modifier.fillMaxSize()) {
            val travelAlpha = (1f - approach) * .35f
            if (travelAlpha > 0f) {
                val horizon = size.height * .50f
                for (line in 0..9) {
                    val depth = (line / 10f + approach * .75f) % 1f
                    val y = horizon + (size.height - horizon) * depth * depth
                    drawLine(palette.accent.copy(alpha = travelAlpha * depth),
                        Offset(0f, y), Offset(size.width, y), 1f + depth * 2f)
                }
            }
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .50f to Color.Transparent,
                .68f to palette.background.copy(alpha = if (palette.light) .08f else .16f),
                1f to palette.background.copy(alpha = if (palette.light) .28f else .48f),
                endY = size.height))
        }
    }
}

private fun DrawScope.drawAnimatedSky(
    artwork: ImageBitmap,
    palette: GamePalette,
    phase: Float,
    animationsEnabled: Boolean
) {
    val skyBottom = size.height * .43f
    clipRect(bottom = skyBottom) {
        drawDriftingCloudBand(artwork, palette, phase, animationsEnabled)

        if (palette.light) {
            val shimmer = if (animationsEnabled)
                (.5f + .5f * sin((phase * 2f * PI).toFloat())) else .55f
            val sun = Offset(size.width * .77f, size.height * .16f)
            drawCircle(Color(0xFFFFE7A0).copy(alpha = .045f + shimmer * .035f),
                radius = size.minDimension * (.11f + shimmer * .012f), center = sun)
            drawCircle(Color.White.copy(alpha = .035f + shimmer * .025f),
                radius = size.minDimension * (.065f + shimmer * .006f), center = sun)
        } else {
            menuSkyStars.forEach { star ->
                val twinkle = if (animationsEnabled)
                    .5f + .5f * sin(((phase + star.phase) * 2f * PI).toFloat()) else .55f
                drawCircle(Color.White.copy(alpha = .12f + twinkle * .26f),
                    radius = star.radius * density, center = Offset(size.width * star.x, size.height * star.y))
            }
            val towerStar = Offset(size.width * .50f, size.height * .205f)
            val glow = if (animationsEnabled)
                .5f + .5f * sin(((phase * 1.6f + .2f) * 2f * PI).toFloat()) else .55f
            drawCircle(Color(0xFFFF3048).copy(alpha = .05f + glow * .08f),
                radius = size.minDimension * (.022f + glow * .005f), center = towerStar)
            drawCircle(Color(0xFFFF5A67).copy(alpha = .20f + glow * .16f),
                radius = size.minDimension * .005f, center = towerStar)
        }
    }
}

/**
 * Repaints the artwork's own cloud band with a tiny horizontal drift, replacing the stationary
 * pixels in that band rather than adding duplicate clouds on top of them.
 */
private fun DrawScope.drawDriftingCloudBand(
    artwork: ImageBitmap,
    palette: GamePalette,
    phase: Float,
    animationsEnabled: Boolean
) {
    val scale = maxOf(size.width / artwork.width, size.height / artwork.height)
    val drawnWidth = artwork.width * scale
    val drawnHeight = artwork.height * scale
    val origin = Offset((size.width - drawnWidth) / 2f, (size.height - drawnHeight) / 2f)
    val shift = if (animationsEnabled)
        sin((phase * 2f * PI).toFloat()) * size.width * .018f else 0f
    val filter = if (palette.light || palette.id == "classic" || palette.id == "synthwave_84")
        null else artworkColorFilter(palette)

    clipRect(top = size.height * .235f, bottom = size.height * .405f) {
        withTransform({
            translate(origin.x + shift, origin.y)
            scale(scale, scale, Offset.Zero)
        }) {
            drawImage(artwork, colorFilter = filter)
        }
    }
}

/** Recolors RGB neon artwork with the selected dark theme's semantic piece colors. */
private fun artworkColorFilter(palette: GamePalette): ColorFilter {
    val red = palette.piece(Tetromino.Z)
    val green = palette.piece(Tetromino.S)
    val blue = palette.piece(Tetromino.J)
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        red.red * .6f, green.red * .6f, blue.red * .6f, 0f, 0f,
        red.green * .6f, green.green * .6f, blue.green * .6f, 0f, 0f,
        red.blue * .6f, green.blue * .6f, blue.blue * .6f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )))
}
