package ru.itoltec.swypetris

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Exports only an image; the ongoing run and its result journal are untouched. */
@Composable
internal fun RecordShareAction(score: Long, playerName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember(score) { mutableStateOf(false) }
    var failed by remember(score) { mutableStateOf(false) }
    Box(modifier) {
        IconButton(enabled = !sharing, modifier = Modifier.size(48.dp).testTag("shareRoundRecord"), onClick = {
            scope.launch {
                sharing = true
                failed = false
                var image: Bitmap? = null
                try {
                    val postcard = withContext(Dispatchers.Default) {
                        AbsolutePostcard.create(context, playerName.ifBlank { "Игрок" }, score).also { image = it }
                    }
                    val send = withContext(Dispatchers.IO) { AbsolutePostcard.shareIntent(context, postcard) }
                    context.startActivity(Intent.createChooser(send, "Поделиться рекордом"))
                } catch (_: IOException) { failed = true }
                catch (_: ActivityNotFoundException) { failed = true }
                finally { image?.recycle(); sharing = false }
            }
        }) {
            Icon(Icons.AutoMirrored.Outlined.Send, "Поделиться рекордом", tint = Color.White)
        }
        if (failed) AlertDialog(onDismissRequest = { failed = false },
            title = { Text("Не удалось поделиться") }, text = { Text("Попробуйте ещё раз.") },
            confirmButton = { TextButton(onClick = { failed = false }) { Text("Понятно") } })
    }
}
