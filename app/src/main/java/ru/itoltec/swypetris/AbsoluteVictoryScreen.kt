package ru.itoltec.swypetris

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val POSTCARD_HEIGHT_FRACTION = .55f
private const val POSTCARD_TITLE_TOP = .17f
private const val COMPACT_POSTCARD_TITLE_TOP = .10f
private const val ABSOLUTE_HEADING_WIDTH = 11f
private val NAME_FIELD_BACKGROUND = Color(0xCC001020)
private val ABSOLUTE_TITLE_COLOR = Color(0xFFFFD54F)
private val NAME_FIELD_BORDER = Color(0xFFC6D7FF)
private val POSTCARD_PREVIEW_BACKGROUND = Color(0xFF001020)

/** Shares the ordinary victory's artwork and motion, keeping the generated image static. */
@Composable
internal fun AbsoluteVictoryScreen(model: GameViewModel) {
    val celebration = if (model.postcardReady) VictoryCelebration.POSTCARD else VictoryCelebration.ABSOLUTE
    VictoryScene(model, celebration) { layout ->
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            if (model.postcardReady) PostcardContent(model.absoluteName, maxHeight, layout)
            else Column(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("absoluteVictoryPage"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
                        top = maxHeight * layout.contentTop, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (layout.compact) 8.dp else 12.dp, Alignment.Bottom)) {
                    item {
                        VictoryHeading("АБСОЛЮТНАЯ\nПОБЕДА!", layout,
                            Modifier.testTag("absoluteVictoryTitle"), ABSOLUTE_HEADING_WIDTH)
                    }
                    item { AbsoluteNameEntry(model) }
                }
                AbsoluteAction("Сформировать открытку", "preparePostcard", model.absolute::preparePostcard)
            }
        }
    }
}

@Composable
private fun AbsoluteNameEntry(model: GameViewModel) {
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Максимальный счёт", color = Color.White)
            Text("999 999", color = Color.White, style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.testTag("absoluteScore"))
        }
        Text("Вы — абсолютный победитель!\nКак вас зовут?", color = Color.White, textAlign = TextAlign.Center)
        OutlinedTextField(model.absoluteName, model.absolute::changeName,
            modifier = Modifier.fillMaxWidth().testTag("absoluteName"), singleLine = true,
            placeholder = { Text("Введите имя", color = Color.White) }, isError = model.absoluteNameError,
            shape = CutCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NAME_FIELD_BORDER,
                unfocusedBorderColor = NAME_FIELD_BORDER, focusedTextColor = Color.White,
                unfocusedTextColor = Color.White, errorTextColor = Color.White,
                cursorColor = ABSOLUTE_TITLE_COLOR, focusedContainerColor = NAME_FIELD_BACKGROUND,
                unfocusedContainerColor = NAME_FIELD_BACKGROUND, errorContainerColor = NAME_FIELD_BACKGROUND),
            supportingText = if (model.absoluteNameError) ({ Text("Введите имя, чтобы сформировать открытку") })
                else null)
    }
}

@Composable
private fun PostcardContent(name: String, availableHeight: Dp, layout: VictoryLayout,
    imageDispatcher: CoroutineDispatcher = Dispatchers.Default, shareDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(name) { mutableStateOf<Bitmap?>(null) }
    var error by remember(name) { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    LaunchedEffect(name) {
        bitmap = withContext(imageDispatcher) { AbsolutePostcard.create(context, name) }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("absoluteVictoryPage"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
                top = availableHeight * if (layout.compact) COMPACT_POSTCARD_TITLE_TOP else POSTCARD_TITLE_TOP,
                bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { VictoryHeading("ОТКРЫТКА\nГОТОВА!", layout,
                Modifier.testTag("absoluteVictoryTitle"), ABSOLUTE_HEADING_WIDTH) }
            val image = bitmap
            if (image == null) item { Text("Формируем открытку…", color = Color.White) }
            else item {
                Image(image.asImageBitmap(), "Открытка: $name. Я прошёл Swypetris! Абсолютный победитель. " +
                    "Максимальный счёт 999 999", Modifier.widthIn(max = 440.dp).fillMaxWidth()
                        .height(availableHeight * POSTCARD_HEIGHT_FRACTION)
                        .background(POSTCARD_PREVIEW_BACKGROUND)
                        .testTag("postcardPreview"))
            }
        }
        val image = bitmap
        if (image != null) {
            AbsoluteAction("Поделиться с друзьями", "sharePostcard") {
                if (!sharing) scope.launch {
                    sharing = true
                    error = false
                    try {
                        val send = withContext(shareDispatcher) { AbsolutePostcard.shareIntent(context, image) }
                        context.startActivity(Intent.createChooser(send, "Поделиться открыткой"))
                    } catch (_: IOException) { error = true }
                    catch (_: ActivityNotFoundException) { error = true }
                    finally { sharing = false }
                }
            }
        }
        if (error) Text("Не удалось поделиться открыткой. Попробуйте ещё раз.", color = Color.White)
    }
}

@Composable
private fun AbsoluteAction(label: String, tag: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        contentAlignment = Alignment.Center) {
        VictoryActionButton(label, Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag(tag), onClick)
    }
}
