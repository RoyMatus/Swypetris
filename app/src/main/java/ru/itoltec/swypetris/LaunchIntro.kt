package ru.itoltec.swypetris

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle



private const val ASSEMBLED_LOGO_MILLIS = 2650L
private const val WORDMARK_BANDS = 8


/** Allows tests to simulate the system's disabled-animation setting without changing device settings. */
internal val LocalLaunchIntroAnimations = staticCompositionLocalOf<Boolean?> { null }

/** Runs frames only while the intro is visible; the model survives Activity recreation. */
@Composable
internal fun LaunchIntroClock(model: GameViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val override = LocalLaunchIntroAnimations.current
    val animate = override ?: remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    LaunchedEffect(model, owner, animate) {
        if (!animate) model.navigation.finishLaunchIntro()
        else owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            var remainder = 0L
            while (model.launchIntroPending) {
                val now = withFrameNanos { it }
                val elapsed = (now - last).coerceAtLeast(0) + remainder
                model.advanceLaunchIntro(elapsed / GameRules.NANOS_PER_MILLI)
                remainder = elapsed % GameRules.NANOS_PER_MILLI
                last = now
            }
        }
    }
}

/** Forms the original block-letter wordmark over the approaching cathedral. */
@Composable
internal fun LaunchIntroOverlay(model: GameViewModel, logoBounds: Rect, modifier: Modifier = Modifier) {
    val logo = ImageBitmap.imageResource(R.drawable.swypetris_logo)
    val palette = LocalGamePalette.current
    Canvas(modifier.testTag("launchIntro").semantics {
        contentDescription = "Заставка SWYPETRIS. Коснитесь, чтобы пропустить"
        onClick("Пропустить заставку") { model.navigation.finishLaunchIntro(); true }
    }.pointerInput(model) { detectTapGestures { model.navigation.finishLaunchIntro() } }) {
        val elapsed = model.launchIntroMillis
        if (logoBounds.isEmpty) return@Canvas
        val scale = minOf(logoBounds.width / logo.width, logoBounds.height / logo.height)
        val origin = logoBounds.center - Offset(logo.width * scale / 2, logo.height * scale / 2)
        // Once assembled, GameTitle draws the logo; the layer does not change as buttons appear.
        if (elapsed >= ASSEMBLED_LOGO_MILLIS) return@Canvas
        val revealWidth = (logo.width * LaunchIntroMotion.wordmarkReveal(elapsed)).toInt()
        if (revealWidth == 0) return@Canvas
        val top = (logo.height * .69f).toInt()
        val bandHeight = (logo.height - top) / 8
        repeat(WORDMARK_BANDS) { band ->
            val sourceY = top + band * bandHeight
            val height = if (band == 7) logo.height - sourceY else bandHeight
            val sourceX = if (band % 2 == 0) 0 else logo.width - revealWidth
            withTransform({
                translate(origin.x + sourceX * scale, origin.y + sourceY * scale)
                scale(scale, scale, Offset.Zero)
            }) {
                drawWordmarkSliceWithOutline(
                    logo = logo,
                    srcOffset = IntOffset(sourceX, sourceY),
                    srcSize = IntSize(revealWidth, height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(revealWidth, height),
                    palette = palette
                )
            }
        }
    }
}
