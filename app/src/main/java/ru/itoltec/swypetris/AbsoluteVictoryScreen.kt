package ru.itoltec.swypetris

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val POSTCARD_HEIGHT_FRACTION = .60f
private val NAME_FIELD_BACKGROUND = Color(0xCC001020)
private const val TITLE_SHADOW_RADIUS = 4f
private val ABSOLUTE_TITLE_STYLE = TextStyle(shadow = Shadow(Color.Black, Offset(1f, 2f), TITLE_SHADOW_RADIUS))
private val ABSOLUTE_TITLE_COLOR = Color(0xFFFFD54F)

/** Shares the ordinary victory's artwork and motion, keeping the generated image static. */
@Composable
internal fun AbsoluteVictoryScreen(model: GameViewModel) {
    VictoryScene(model) { layout ->
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val topSpace = if (model.postcardReady) 12.dp else maxHeight * layout.contentTop
            LazyColumn(Modifier.fillMaxSize().testTag("absoluteVictoryPage"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topSpace, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom)) {
                item {
                    val title = if (model.postcardReady) "ОТКРЫТКА ГОТОВА!" else "АБСОЛЮТНАЯ ПОБЕДА!"
                    val font = minOf(32f, maxWidth.value / 11f / LocalDensity.current.fontScale).sp
                    Text(title, Modifier.fillMaxWidth().testTag("absoluteVictoryTitle"),
                        color = ABSOLUTE_TITLE_COLOR, fontSize = font, fontWeight = FontWeight.Black,
                        style = ABSOLUTE_TITLE_STYLE,
                        textAlign = TextAlign.Center)
                }
                if (model.postcardReady) item {
                    PostcardContent(model.absoluteName, maxHeight * POSTCARD_HEIGHT_FRACTION)
                }
                else item { AbsoluteNameEntry(model) }
            }
        }
    }
}

@Composable
private fun AbsoluteNameEntry(model: GameViewModel) {
    androidx.compose.foundation.layout.Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("999 999", color = Color.White, style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.testTag("absoluteScore"))
        Text("Вы — абсолютный победитель! Как вас зовут?", color = Color.White, textAlign = TextAlign.Center)
        OutlinedTextField(model.absoluteName, model.absolute::changeName,
            modifier = Modifier.fillMaxWidth().testTag("absoluteName"), singleLine = true,
            placeholder = { Text("Введите имя", color = Color.White) }, isError = model.absoluteNameError,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
                unfocusedTextColor = Color.White, errorTextColor = Color.White,
                cursorColor = ABSOLUTE_TITLE_COLOR, focusedContainerColor = NAME_FIELD_BACKGROUND,
                unfocusedContainerColor = NAME_FIELD_BACKGROUND, errorContainerColor = NAME_FIELD_BACKGROUND),
            supportingText = if (model.absoluteNameError) ({ Text("Введите имя, чтобы сформировать открытку") })
                else null)
        AppActionButton("Сформировать открытку", ActionStyle.PRIMARY,
            Modifier.fillMaxWidth().testTag("preparePostcard"), LocalGamePalette.current.piece(Tetromino.S),
            model.absolute::preparePostcard)
    }
}

@Composable
private fun PostcardContent(name: String, previewHeight: Dp,
    imageDispatcher: CoroutineDispatcher = Dispatchers.Default, shareDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(name) { mutableStateOf<Bitmap?>(null) }
    var error by remember(name) { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    LaunchedEffect(name) {
        bitmap = withContext(imageDispatcher) { AbsolutePostcard.create(context, name) }
    }
    androidx.compose.foundation.layout.Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val image = bitmap
        if (image == null) Text("Формируем открытку…", color = Color.White)
        else {
            Image(image.asImageBitmap(), "Открытка: $name. Я прошёл Swypetris! Абсолютный победитель. " +
                "Максимальный счёт 999 999", Modifier.fillMaxWidth().height(previewHeight)
                    .testTag("postcardPreview"))
            AppActionButton("Поделиться с друзьями", ActionStyle.PRIMARY,
                Modifier.fillMaxWidth().testTag("sharePostcard"), LocalGamePalette.current.piece(Tetromino.S)) {
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
