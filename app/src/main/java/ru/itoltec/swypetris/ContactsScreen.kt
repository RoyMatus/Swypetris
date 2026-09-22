package ru.itoltec.swypetris

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Контакт разработчика с адресом для показа, копирования и открытия внешним приложением. */
internal enum class DeveloperContact(val title: String, val address: String, val uri: String) {
    EMAIL("Email", "piligrim18@gmail.com", "mailto:piligrim18@gmail.com"),
    TELEGRAM("Telegram", "@RoyMatus", "https://t.me/RoyMatus"),
    WEBSITE("Сайт", "https://itoltec.ru/", "https://itoltec.ru/");

    /** Создаёт почтовое или браузерное действие без отправки сообщения от имени пользователя. */
    fun intent(): Intent = Intent(if (this == EMAIL) Intent.ACTION_SENDTO else Intent.ACTION_VIEW, Uri.parse(uri))
}

/** Открывает контакт; отсутствие подходящего приложения обрабатывается интерфейсом без аварии. */
internal fun openContact(context: Context, contact: DeveloperContact): Boolean = try {
    context.startActivity(contact.intent())
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Контакты в цветных карточках; переход на страницу сохраняет текущую партию на паузе. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ContactsScreen(model: GameViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val messages = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(Modifier.fillMaxSize().testTag("contactsPage"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { GameTitle() }
            item { Text("Контакты", style = MaterialTheme.typography.headlineLarge) }
            DeveloperContact.entries.forEachIndexed { index, contact ->
                item {
                    val accent = listOf(LocalGamePalette.current.accent, LocalGamePalette.current.secondary, LocalGamePalette.current.gold)[index]
                    Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = .08f), border = BorderStroke(1.dp, accent.copy(alpha = .4f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(contact.title, color = accent, style = MaterialTheme.typography.titleLarge)
                            Text(contact.address, style = MaterialTheme.typography.bodyLarge)
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    if (!openContact(context, contact)) scope.launch {
                                        messages.showSnackbar("Нет приложения для открытия. Скопируйте адрес.")
                                    }
                                }, modifier = Modifier.testTag("open${contact.name}")) { Text("Открыть") }
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(contact.address))
                                    scope.launch { messages.showSnackbar("Адрес скопирован") }
                                }, modifier = Modifier.testTag("copy${contact.name}")) { Text("Копировать") }
                            }
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = model::privacy, modifier = Modifier.testTag("privacy")) { Text("Конфиденциальность") } }
            item { Button(onClick = model::menu, modifier = Modifier.testTag("contactsBack")) { Text("В меню") } }
        }
        SnackbarHost(messages, Modifier.align(Alignment.BottomCenter))
    }
}
