package ru.itoltec.swypetris

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.graphics.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import org.json.JSONArray
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.json.JSONObject

/** Test override for a static sky while verifying unrelated transparent UI. */
internal val LocalMenuSkyAnimations = staticCompositionLocalOf<Boolean?> { null }

/** The reference Red Square composition has matching night and day lighting. */
@Composable
internal fun ThemeBackdrop(palette: GamePalette, approach: Float = 1f) {
    val enabled = LocalMenuSkyAnimations.current ?:
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())
    val seconds = rememberMenuSkyTime(enabled)
    MenuSkyArtwork(palette, approach) { if (enabled) seconds.value else 0.0 }
}

@Composable
internal fun rememberMenuSkyTime(enabled: Boolean): State<Double> {
    val owner = LocalLifecycleOwner.current
    // Read time only during drawing: no per-frame bitmap loading or menu recomposition.
    val seconds = remember { mutableStateOf(0.0) }
    LaunchedEffect(owner, enabled) {
        if (enabled) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withInfiniteAnimationFrameNanos { it }
            while (true) {
                val now = withInfiniteAnimationFrameNanos { it }
                seconds.value += (now - last).coerceIn(0L, 100_000_000L) / 1_000_000_000.0
                last = now
            }
        }
    }
    return seconds
}

/** Shared renderer also permits deterministic Android/reference frame comparisons. */
@Composable
internal fun MenuSkyArtwork(palette: GamePalette, approach: Float, seconds: () -> Double) {
    val artwork = ImageBitmap
        .imageResource(if (palette.light) R.drawable.red_square_day else R.drawable.red_square_synthwave)
    val skyPatch = ImageBitmap.imageResource(if (palette.light) R.drawable.menu_sky_day else R.drawable.menu_sky_night)
    val sprites = if (palette.light) listOf(R.drawable.menu_cloud_day_0, R.drawable.menu_cloud_day_1,
        R.drawable.menu_cloud_day_2, R.drawable.menu_cloud_day_3) else listOf(R.drawable.menu_cloud_night_0,
        R.drawable.menu_cloud_night_1, R.drawable.menu_cloud_night_2, R.drawable.menu_cloud_night_3)
    val clouds = sprites.map { ImageBitmap.imageResource(it) }
    val resources = LocalContext.current.resources
    val registration = remember(resources) {
        val json = resources.openRawResource(R.raw.menu_sky_registration).bufferedReader().use { JSONObject(it
            .readText()) }
        val path = registrationPath(json.getJSONArray("cloudRuns"))
        val sky = registrationPath(json.getJSONArray("skyRuns"))
        val stars = json.getJSONArray("stars")
        Triple(path, sky, List(stars.length()) { i -> stars.getJSONArray(i).let { Offset(it.getDouble(0).toFloat(),
            it.getDouble(1).toFloat()) } })
    }
    val filter = remember(palette) {
        if (palette.light || palette.id == "classic" ||
            palette.id == "synthwave_84") null else artworkColorFilter(palette)
    }
    // Precompute radial gradients once; alpha alone changes during each frame.
    val halos = remember(palette.light) {
        listOf(skyHalo(11f, Color(0xFFBACCFF)), skyHalo(if (palette.light) 45f else 69f, Color(0xFFFF2359)),
            skyHalo(21f, Color(0xFFFF6373)))
    }
    val approachLayer = Modifier.fillMaxSize().graphicsLayer {
        scaleX = 1f + .08f * approach
        scaleY = scaleX
        transformOrigin = TransformOrigin(.5f, .28f)
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(approachLayer) {
            val scale = maxOf(size.width / MenuSkyMotion.WIDTH, size.height / MenuSkyMotion.HEIGHT)
            val time = seconds()
            withTransform({
                translate((size.width - MenuSkyMotion.WIDTH * scale) / 2f,
                    (size.height - MenuSkyMotion.HEIGHT * scale) / 2f)
                scale(scale, scale, Offset.Zero)
            }) {
                drawImage(artwork, colorFilter = filter)
                drawImage(skyPatch, colorFilter = filter)
                clipPath(registration.first) {
                    MenuSkyMotion.clouds.forEachIndexed { i, cloud ->
                        val image = clouds[i]
                        val spriteScale = cloud.width / image.width
                        withTransform({ translate(cloud.x(time), cloud.y); scale(spriteScale, spriteScale,
                            Offset.Zero) }) {
                            drawImage(image, colorFilter = filter)
                        }
                    }
                }
                clipPath(registration.second) {
                    if (!palette.light) registration.third.forEachIndexed { i, star ->
                        drawHalo(halos[0], star, MenuSkyMotion.twinkle(time, i))
                    }
                }
                val pulse = MenuSkyMotion.towerPulse(time)
                val star = Offset(92f, 395f)
                drawHalo(halos[1], star, (if (palette.light) .37f else .80f) * pulse)
                drawHalo(halos[2], star, (if (palette.light) .18f else .50f) * pulse)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val travelAlpha = (1f - approach) * .35f
            if (travelAlpha > 0f) {
                val horizon = size.height * .50f
                for (line in 0..9) {
                    val depth = (line / 10f + approach * .75f) % 1f
                    val y = horizon + (size.height - horizon) * depth * depth
                    drawLine(palette.accent.copy(alpha = travelAlpha * depth), Offset(0f, y), Offset(size.width,
                        y), 1f + depth * 2f)
                }
            }
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .50f to Color.Transparent,
                .68f to palette.background.copy(alpha = if (palette.light) .08f else .16f),
                1f to palette.background.copy(alpha = if (palette.light) .28f else .48f), endY = size.height))
        }
    }
}

private fun registrationPath(runs: JSONArray): Path = Path().apply {
    for (i in 0 until runs.length()) {
        val run = runs.getJSONArray(i)
        val x = run.getInt(0) * MenuSkyMotion.WIDTH / 640
        val y = run.getInt(1) * MenuSkyMotion.HEIGHT / 1137
        addRect(Rect(x, y, x + run.getInt(2) * MenuSkyMotion.WIDTH / 640, y + MenuSkyMotion.HEIGHT / 1137))
    }
}
private fun skyHalo(radius: Float, color: Color): ImageBitmap {
    val image = ImageBitmap((radius * 2).toInt(), (radius * 2).toInt())
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
    ) {
        drawRect(Brush.radialGradient(0f to color, .27f to color.copy(alpha = .48f), 1f to color.copy(alpha = 0f),
            center = Offset(radius, radius), radius = radius))
    }
    return image
}

private fun DrawScope.drawHalo(image: ImageBitmap, center: Offset, alpha: Float) {
    drawImage(image, topLeft = center - Offset(image.width / 2f, image.height / 2f), alpha = alpha)
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
