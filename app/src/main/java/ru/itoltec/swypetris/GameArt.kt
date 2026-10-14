package ru.itoltec.swypetris

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

private const val MAX_MENU_FRAME_NANOS = 100_000_000L
private const val NANOS_PER_MENU_SECOND = 1_000_000_000.0
private const val MENU_APPROACH_ZOOM = .08f
private const val MENU_APPROACH_PIVOT_X = .5f
private const val MENU_APPROACH_PIVOT_Y = .28f

/** Test override: false disables decoration without changing native feedback. */
internal val LocalMenuSkyAnimations = staticCompositionLocalOf<Boolean?> { null }

@Composable
private fun menuAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val enabled = remember(resolver) { mutableStateOf(systemAnimationsEnabled(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled.value = systemAnimationsEnabled(resolver) }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled.value && LocalMenuSkyAnimations.current != false
}

private fun systemAnimationsEnabled(resolver: ContentResolver): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()) &&
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/** Called only by the visible MENU owner; other pages use the static header. */
@Composable
internal fun ThemeBackdrop(palette: GamePalette, approach: Float = 1f) {
    val enabled = menuAnimationsEnabled()
    val seconds = rememberMenuSkyTime(enabled, palette.id)
    MenuSkyArtwork(palette, approach) { if (enabled) seconds.value else 0.0 }
}

@Composable
internal fun rememberMenuSkyTime(enabled: Boolean, themeId: String = ""): State<Double> {
    val owner = LocalLifecycleOwner.current
    val seconds = remember(themeId, enabled) { mutableStateOf(0.0) }
    LaunchedEffect(owner, enabled, themeId) {
        if (enabled) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withInfiniteAnimationFrameNanos { it }
            while (true) {
                val now = withInfiniteAnimationFrameNanos { it }
                seconds.value += (now - last).coerceIn(0L, MAX_MENU_FRAME_NANOS) / NANOS_PER_MENU_SECOND
                last = now
            }
        }
    }
    return seconds
}

/** Deterministic renderer entry point shared with authored-frame instrumentation. */
@Composable
internal fun MenuSkyArtwork(palette: GamePalette, approach: Float, seconds: () -> Double) {
    val resources = LocalContext.current.resources
    val scene = remember(resources, palette.id) { loadMenuScene(resources, palette.id) }
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        scaleX = 1f + MENU_APPROACH_ZOOM * (1f - approach)
        scaleY = scaleX
        transformOrigin = TransformOrigin(MENU_APPROACH_PIVOT_X, MENU_APPROACH_PIVOT_Y)
        clip = true
    }) {
        drawIntoCanvas { scene.draw(it.nativeCanvas, size.width, size.height, seconds()) }
    }
}

/** Static source crop for menu pages. It owns no timeline, sprite or frame scheduling. */
@Composable
internal fun MenuSceneHeader(palette: GamePalette, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val plate = remember(resources, palette.id) { loadMenuPlate(resources, palette.id) }
    val header = remember(plate) { MenuHeaderArtwork(plate) }
    Canvas(modifier) {
        drawIntoCanvas { header.draw(it.nativeCanvas, size.width, size.height) }
    }
}
