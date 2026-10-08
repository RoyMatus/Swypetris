package ru.itoltec.swypetris

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    var showShareApp by remember { mutableStateOf(false) }
    var showApkDownload by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().navigationBarsPadding().testTag("contactsPage"),
            contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenArtHeader("Контакты", "Связаться с разработчиком") }
            DeveloperContact.entries.forEachIndexed { index, contact ->
                item {
                    ContactCard(contact, index, onOpen = {
                        if (!openContact(context, contact)) scope.launch {
                            messages.showSnackbar("Нет приложения для открытия. Скопируйте адрес.")
                        }
                    }, onCopy = {
                        clipboard.setText(AnnotatedString(contact.address))
                        scope.launch { messages.showSnackbar("Адрес скопирован") }
                    })
                }
            }
            item { ShareAppSection(onStore = { showShareApp = true }, onApk = { showApkDownload = true }) }
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
                            Modifier.fillMaxWidth().testTag("legal"), palette.piece(Tetromino.J),
                                model.navigation::legal)
                        AppActionButton("Конфиденциальность", ActionStyle.SECONDARY,
                            Modifier.fillMaxWidth().testTag("privacy"), accent, model.navigation::privacy)
                    }
                }
            }
        }
        SnackbarHost(messages, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    if (showShareApp) ShareAppDialog(onDismiss = { showShareApp = false })
    if (showApkDownload) ApkDownloadDialog(onDismiss = { showApkDownload = false })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ContactCard(contact: DeveloperContact, index: Int, onOpen: () -> Unit, onCopy: () -> Unit) {
    val palette = LocalGamePalette.current
    val accent = listOf(palette.accent, palette.secondary, palette.gold)[index]
    ThemedCard(accent, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(contact.title.uppercase(), color = accent, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            SettingsDivider(accent)
            Text(contact.address, color = palette.text, style = MaterialTheme.typography.bodyLarge)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppActionButton("Открыть", ActionStyle.TEXT,
                    Modifier.testTag("open${contact.name}"), accent) {
                    onOpen()
                }
                AppActionButton("Копировать", ActionStyle.TEXT, Modifier.testTag("copy${contact.name}"),
                    LocalGamePalette.current.piece(Tetromino.J)) {
                    onCopy()
                }
            }
        }
    }
}

@Composable
private fun ShareAppSection(onStore: () -> Unit, onApk: () -> Unit) {
    val palette = LocalGamePalette.current
    val accent = palette.piece(Tetromino.S)
    ThemedCard(accent, Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("shareAppSection")) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ПОДЕЛИТЬСЯ ПРИЛОЖЕНИЕМ", color = accent, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            SettingsDivider(accent)
            AppActionButton("RuStore", ActionStyle.SECONDARY,
                Modifier.fillMaxWidth().testTag("shareApp"), palette.piece(Tetromino.S)) {
                onStore()
            }
            AppActionButton("Скачать APK", ActionStyle.SECONDARY,
                Modifier.fillMaxWidth().testTag("downloadApk"), palette.piece(Tetromino.S)) {
                onApk()
            }
        }
    }
}
