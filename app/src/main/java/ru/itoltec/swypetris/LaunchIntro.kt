package ru.itoltec.swypetris

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
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

/** Позволяет проверять системное отключение анимации без изменения настроек устройства. */
internal val LocalLaunchIntroAnimations = staticCompositionLocalOf<Boolean?> { null }

/** Запускает кадры только на видимом экране; сама модель переживает пересоздание Activity. */
@Composable
internal fun LaunchIntroClock(model: GameViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val override = LocalLaunchIntroAnimations.current
    val animate = override ?: remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    LaunchedEffect(model, owner, animate) {
        if (!animate) model.finishLaunchIntro()
        else owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            var remainder = 0L
            while (model.launchIntroPending) {
                val now = withFrameNanos { it }
                val elapsed = (now - last).coerceAtLeast(0) + remainder
                model.advanceLaunchIntro(elapsed / 1_000_000L)
                remainder = elapsed % 1_000_000L
                last = now
            }
        }
    }
}

/** Рисует части исходного PNG поверх всего экрана: начало траекторий находится за его краями. */
@Composable
internal fun LaunchIntroOverlay(model: GameViewModel, logoBounds: Rect, modifier: Modifier = Modifier) {
    val logo = ImageBitmap.imageResource(R.drawable.swypetris_logo)
    Canvas(modifier.testTag("launchIntro").semantics {
        contentDescription = "Заставка SWYPETRIS. Коснитесь, чтобы пропустить"
        onClick("Пропустить заставку") { model.finishLaunchIntro(); true }
    }.pointerInput(model) { detectTapGestures { model.finishLaunchIntro() } }) {
        val elapsed = model.launchIntroMillis
        if (logoBounds.isEmpty) return@Canvas
        val scale = minOf(logoBounds.width / logo.width, logoBounds.height / logo.height)
        val origin = logoBounds.center - Offset(logo.width * scale / 2, logo.height * scale / 2)
        // Собранный логотип уже рисует GameTitle: при появлении кнопок слой не меняется.
        if (elapsed >= 2650L) return@Canvas
        LogoPieces.parts.forEachIndexed { index, piece ->
            val target = origin + Offset(piece.x * scale, piece.y * scale)
            val position: Offset
            val pivot: Offset
            val angle: Float
            if (piece.stripe) {
                val progress = LaunchIntroMotion.stripeProgress(elapsed, index, LogoPieces.stripeCount)
                val startX = if (index % 2 == 0) -piece.width * scale - 1 else size.width + 1
                position = Offset(startX + (target.x - startX) * progress, target.y)
                pivot = position
                angle = 0f
            } else {
                val cube = piece.cube
                val anchor = LogoPieces.cubes[cube]
                val destination = origin + Offset(anchor.x * scale, anchor.y * scale)
                val progress = LaunchIntroMotion.cubeProgress(elapsed, cube)
                val horizontal = LaunchIntroMotion.easeOut(progress)
                val startX = (LaunchIntroMotion.seed(cube, 29) % 1001) / 1000f * size.width
                val startY = -anchor.height * scale * 2 - size.height *
                    (LaunchIntroMotion.seed(cube, 43) % 301) / 1000f
                val movingAnchor = Offset(startX + (destination.x - startX) * horizontal,
                    startY + (destination.y - startY) * LaunchIntroMotion.fallProgress(progress))
                position = movingAnchor + Offset((piece.x - anchor.x) * scale, (piece.y - anchor.y) * scale)
                pivot = movingAnchor + Offset(anchor.width * scale / 2, anchor.height * scale / 2)
                angle = ((LaunchIntroMotion.seed(cube, 59) % 61) - 30) * (1f - horizontal)
            }
            rotate(angle, pivot) {
                withTransform({ translate(position.x, position.y); scale(scale, scale, Offset.Zero) }) {
                    drawImage(logo, srcOffset = IntOffset(piece.x, piece.y),
                        srcSize = IntSize(piece.width, piece.height),
                        dstOffset = IntOffset.Zero, dstSize = IntSize(piece.width, piece.height))
                }
            }
        }
    }
}
