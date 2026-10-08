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

private const val MAX_SKY_FRAME_NANOS = 100_000_000L
private const val NANOS_PER_SKY_SECOND = 1_000_000_000.0
private const val LIGHT_OUTER_TOWER_ALPHA = .37f
private const val DARK_OUTER_TOWER_ALPHA = .80f
private const val LIGHT_INNER_TOWER_ALPHA = .18f
private const val DARK_INNER_TOWER_ALPHA = .50f
private const val TRAVEL_GRID_LINES = 10
private const val LOWER_FADE_START = .50f
private const val LOWER_FADE_MIDPOINT = .68f
private const val REGISTRATION_WIDTH = 640
private const val REGISTRATION_HEIGHT = 1137
private const val HALO_FALLOFF_STOP = .27f
private const val ARTWORK_CHANNEL_GAIN = .6f


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
                seconds.value += (now - last).coerceIn(0L, MAX_SKY_FRAME_NANOS) / NANOS_PER_SKY_SECOND
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
    val clouds = menuCloudSprites(palette.light)
    val resources = LocalContext.current.resources
    val registration = remember(resources) { readSkyRegistration(resources) }
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
                    drawMenuCloudSprites(clouds, time, filter)
                }
                clipPath(registration.second) {
                    if (!palette.light) registration.third.forEachIndexed { i, star ->
                        drawHalo(halos[0], star, MenuSkyMotion.twinkle(time, i))
                    }
                }
                val pulse = MenuSkyMotion.towerPulse(time)
                val star = Offset(92f, 395f)
                drawHalo(halos[1], star, (if (
                    palette.light) LIGHT_OUTER_TOWER_ALPHA else DARK_OUTER_TOWER_ALPHA) * pulse)
                drawHalo(halos[2], star, (if (
                    palette.light) LIGHT_INNER_TOWER_ALPHA else DARK_INNER_TOWER_ALPHA) * pulse)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawMenuTravelGrid(palette, approach)
        }
    }
}

private fun DrawScope.drawMenuCloudSprites(clouds: List<ImageBitmap>, time: Double, filter: ColorFilter?) {
    MenuSkyMotion.clouds.forEachIndexed { i, cloud ->
        val image = clouds[i]
        val spriteScale = cloud.width / image.width
        withTransform({ translate(cloud.x(time), cloud.y); scale(spriteScale, spriteScale,
            Offset.Zero) }) {
            drawImage(image, colorFilter = filter)
        }
    }
}

@Composable
private fun menuCloudSprites(light: Boolean): List<ImageBitmap> {
    val sprites = if (light) listOf(R.drawable.menu_cloud_day_0, R.drawable.menu_cloud_day_1,
        R.drawable.menu_cloud_day_2, R.drawable.menu_cloud_day_3) else listOf(R.drawable.menu_cloud_night_0,
        R.drawable.menu_cloud_night_1, R.drawable.menu_cloud_night_2, R.drawable.menu_cloud_night_3)
    return sprites.map { ImageBitmap.imageResource(it) }
}

private fun registrationPath(runs: JSONArray): Path = Path().apply {
    for (i in 0 until runs.length()) {
        val run = runs.getJSONArray(i)
        val x = run.getInt(0) * MenuSkyMotion.WIDTH / REGISTRATION_WIDTH
        val y = run.getInt(1) * MenuSkyMotion.HEIGHT / REGISTRATION_HEIGHT
        addRect(Rect(x, y, x + run.getInt(2) * MenuSkyMotion.WIDTH / REGISTRATION_WIDTH,
            y + MenuSkyMotion.HEIGHT / REGISTRATION_HEIGHT))
    }
}
private fun skyHalo(radius: Float, color: Color): ImageBitmap {
    val image = ImageBitmap((radius * 2).toInt(), (radius * 2).toInt())
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
    ) {
        drawRect(Brush.radialGradient(0f to color, HALO_FALLOFF_STOP to color.copy(alpha = .48f),
            1f to color.copy(alpha = 0f),
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
        red.red * ARTWORK_CHANNEL_GAIN, green.red * ARTWORK_CHANNEL_GAIN, blue.red * ARTWORK_CHANNEL_GAIN, 0f, 0f,
        red.green * ARTWORK_CHANNEL_GAIN, green.green * ARTWORK_CHANNEL_GAIN, blue.green * ARTWORK_CHANNEL_GAIN, 0f, 0f,
        red.blue * ARTWORK_CHANNEL_GAIN, green.blue * ARTWORK_CHANNEL_GAIN, blue.blue * ARTWORK_CHANNEL_GAIN, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )))
}

private fun DrawScope.drawMenuTravelGrid(palette: GamePalette, approach: Float) {
    val travelAlpha = (1f - approach) * .35f
    if (travelAlpha > 0f) {
        val horizon = size.height * .50f
        for (line in 0 until TRAVEL_GRID_LINES) {
            val depth = (line / 10f + approach * .75f) % 1f
            val y = horizon + (size.height - horizon) * depth * depth
            drawLine(palette.accent.copy(alpha = travelAlpha * depth), Offset(0f, y), Offset(size.width,
                y), 1f + depth * 2f)
        }
    }
    drawRect(Brush.verticalGradient(0f to Color.Transparent, LOWER_FADE_START to Color.Transparent,
        LOWER_FADE_MIDPOINT to palette.background.copy(alpha = if (palette.light) .08f else .16f),
        1f to palette.background.copy(alpha = if (palette.light) .28f else .48f), endY = size.height))
}

private fun readSkyRegistration(resources: android.content.res.Resources): Triple<Path, Path, List<Offset>> {
    val json = resources.openRawResource(R.raw.menu_sky_registration).bufferedReader().use { JSONObject(it
        .readText()) }
    val path = registrationPath(json.getJSONArray("cloudRuns"))
    val sky = registrationPath(json.getJSONArray("skyRuns"))
    val stars = json.getJSONArray("stars")
    return Triple(path, sky, List(stars.length()) { i -> stars.getJSONArray(i).let { Offset(it.getDouble(0).toFloat(),
        it.getDouble(1).toFloat()) } })
}
