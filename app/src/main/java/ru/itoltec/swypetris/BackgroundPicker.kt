package ru.itoltec.swypetris

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val BACKGROUND_VEIL_ALPHA = .72f

@Composable
internal fun BackgroundPicker(background: CustomBackground) {
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        background.choose(resolver, it)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().testTag("customBackgroundEnabled")
            .toggleable(background.enabled, role = Role.Checkbox, onValueChange = background::changeEnabled),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(background.enabled, onCheckedChange = null)
            Text("Использовать свой фон", Modifier.weight(1f))
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !background.busy,
            modifier = Modifier.fillMaxWidth().testTag("chooseBackground")) {
            Text(if (background.busy) "Загрузка…" else "Выбрать файл")
        }
        Text("Изображение до 16 МиБ. Оригинал не изменяется.")
        background.error?.let { Text(it, Modifier.testTag("backgroundError")) }
    }
}

/** Only a backdrop layer; the grid, pieces and HUD retain their original coordinates/colors. */
@Composable
internal fun BackgroundImageLayer(image: BackgroundImage?, crop: BackgroundCrop) {
    if (image == null) return
    val bitmap = remember(image) { image.bitmap.asImageBitmap() }
    val light = LocalGamePalette.current.light
    Canvas(Modifier.fillMaxSize().testTag("customBackground")) {
        val placement = backgroundPlacement(bitmap.width.toFloat(), bitmap.height.toFloat(),
            size.width, size.height, crop)
        if (placement.width <= 0f || placement.height <= 0f) return@Canvas
        drawImage(bitmap, dstOffset = IntOffset(placement.left.roundToInt(), placement.top.roundToInt()),
            dstSize = IntSize(placement.width.roundToInt(), placement.height.roundToInt()))
        drawRect((if (light) Color.White else Color.Black).copy(alpha = BACKGROUND_VEIL_ALPHA))
    }
}

/** Full-window portrait preview uses the exact gameplay geometry, with controls overlaid. */
@Composable
internal fun BackgroundCropPreview(background: CustomBackground) {
    val palette = LocalGamePalette.current
    val density = LocalDensity.current
    val safeTop = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density).toFloat()
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .testTag("backgroundCropPreview")) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        BackgroundImageLayer(background.draft, background.draftCrop)
        GameGridBackground(gameplayGeometry(height, safeTop))
        Box(Modifier.fillMaxSize().testTag("backgroundCropGestures")
            .pointerInput(background, width, height) {
                detectTransformGestures { focus, pan, zoom, _ ->
                    background.transform(width, height, BackgroundTransform(focus.x, focus.y, pan.x, pan.y, zoom))
                }
            })
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.background(palette.background.copy(alpha = .9f)).padding(8.dp)) {
                Text("Двумя пальцами — масштаб, одним — положение", color = palette.text)
                background.error?.let { Text(it, color = palette.text) }
            }
            Row(Modifier.fillMaxWidth().background(palette.background.copy(alpha = .9f)),
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = background::cancel, enabled = !background.busy) { Text("Отмена") }
                TextButton(onClick = background::save, enabled = !background.busy,
                    modifier = Modifier.testTag("saveBackground")) { Text("Готово") }
            }
        }
    }
}
