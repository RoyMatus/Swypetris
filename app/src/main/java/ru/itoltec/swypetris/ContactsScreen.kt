package ru.itoltec.swypetris

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Developer contact with a display address, copyable text, and an external URI. */
internal enum class DeveloperContact(val title: String, val address: String, val uri: String) {
    EMAIL("Email", "piligrim18@gmail.com", "mailto:piligrim18@gmail.com"),
    TELEGRAM("Telegram", "@RoyMatus", "https://t.me/RoyMatus"),
    WEBSITE("Сайт", "https://itoltec.ru/", "https://itoltec.ru/");

    /** Builds an email or browser intent without sending any message on the user's behalf. */
    fun intent(): Intent = Intent(if (this == EMAIL) Intent.ACTION_SENDTO else Intent.ACTION_VIEW, Uri.parse(uri))
}

/** Opens a contact; the UI handles devices without a suitable external application. */
internal fun openContact(context: Context, contact: DeveloperContact): Boolean = try {
    context.startActivity(contact.intent())
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Shows developer contacts in colored cards while the current game remains paused. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ContactsScreen(model: GameViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val messages = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val palette = LocalGamePalette.current
    Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().navigationBarsPadding().testTag("contactsPage"),
            contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenArtHeader("Контакты", "Связаться с разработчиком") }
            DeveloperContact.entries.forEachIndexed { index, contact ->
                item {
                    val accent = listOf(palette.accent, palette.secondary, palette.gold)[index]
                    ThemedCard(accent, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(contact.title.uppercase(), color = accent, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            SettingsDivider(accent)
                            Text(contact.address, color = palette.text, style = MaterialTheme.typography.bodyLarge)
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppActionButton("Открыть", ActionStyle.TEXT, Modifier.testTag("open${contact.name}"), accent) {
                                    if (!openContact(context, contact)) scope.launch {
                                        messages.showSnackbar("Нет приложения для открытия. Скопируйте адрес.")
                                    }
                                }
                                AppActionButton("Копировать", ActionStyle.TEXT, Modifier.testTag("copy${contact.name}"),
                                    LocalGamePalette.current.piece(Tetromino.J)) {
                                    clipboard.setText(AnnotatedString(contact.address))
                                    scope.launch { messages.showSnackbar("Адрес скопирован") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                val accent = palette.piece(Tetromino.T)
                ThemedCard(accent, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("О ПРИЛОЖЕНИИ", color = accent, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        SettingsDivider(accent)
                        Text("Swypetris · © 2026 RoyMatus", color = palette.text,
                            style = MaterialTheme.typography.bodyMedium)
                        AppActionButton("Лицензии и права", ActionStyle.SECONDARY,
                            Modifier.fillMaxWidth().testTag("legal"), palette.piece(Tetromino.J), model::legal)
                        AppActionButton("Конфиденциальность", ActionStyle.SECONDARY,
                            Modifier.fillMaxWidth().testTag("privacy"), accent, model::privacy)
                    }
                }
            }
        }
        SnackbarHost(messages, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
