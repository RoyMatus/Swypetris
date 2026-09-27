package ru.itoltec.swypetris

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** One credited resource and the bundled license asset, if a full license is provided. */
private data class LegalItem(val title: String, val credit: String, val source: String, val licenseAsset: String?)
/** Group of legal notices displayed with a shared heading and introduction. */
private data class LegalSection(val title: String, val introduction: String, val items: List<LegalItem>)

/** Notices are kept in one asset so adding a credited resource needs no screen changes. */
private fun readLegalSections(json: String): List<LegalSection> {
    val sections = JSONObject(json).getJSONArray("sections")
    return (0 until sections.length()).map { sectionIndex ->
        val section = sections.getJSONObject(sectionIndex)
        val items = section.getJSONArray("items")
        LegalSection(section.getString("title"), section.getString("introduction"),
            (0 until items.length()).map { itemIndex ->
                val item = items.getJSONObject(itemIndex)
                LegalItem(item.getString("title"), item.getString("credit"), item.getString("source"),
                    item.optString("licenseAsset").takeIf(String::isNotEmpty))
            })
    }
}

/** Offline attribution and bundled license texts; external sources open only on request. */
@Composable
internal fun LegalScreen() {
    val context = LocalContext.current
    val sections = remember(context) {
        context.assets.open("legal-notices.json").bufferedReader().use { readLegalSections(it.readText()) }
    }
    var licenseText by remember { mutableStateOf<Pair<String, String>?>(null) }
    var linkUnavailable by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("legalPage"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Лицензии и права", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Swypetris · © 2026 RoyMatus", style = MaterialTheme.typography.titleMedium) }
        item { Text("Логотип, значок, иллюстрация кубка, игровые звуки и музыкальные записи созданы для Swypetris. Сведения о сторонних источниках приведены ниже.") }
        sections.forEach { section ->
            item { Text(section.title, style = MaterialTheme.typography.titleLarge) }
            item { Text(section.introduction, style = MaterialTheme.typography.bodyMedium) }
            section.items.forEach { notice ->
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(notice.title, style = MaterialTheme.typography.titleMedium)
                        Text(notice.credit, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppActionButton("Источник", ActionStyle.TEXT,
                                accent = LocalGamePalette.current.piece(Tetromino.J)) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notice.source)))
                                } catch (_: ActivityNotFoundException) {
                                    linkUnavailable = true
                                } catch (_: SecurityException) {
                                    linkUnavailable = true
                                }
                            }
                            notice.licenseAsset?.let { asset ->
                                AppActionButton("Лицензия", ActionStyle.TEXT, Modifier.testTag("license-$asset"),
                                    LocalGamePalette.current.piece(Tetromino.T)) {
                                    licenseText = notice.title to context.assets.open("licenses/$asset")
                                        .bufferedReader().use { it.readText() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    licenseText?.let { (title, body) ->
        AlertDialog(onDismissRequest = { licenseText = null }, title = { Text(title) },
            text = { LazyColumn { item { Text(body) } } },
            confirmButton = { AppActionButton("Закрыть", ActionStyle.TEXT) { licenseText = null } })
    }
    if (linkUnavailable) AlertDialog(onDismissRequest = { linkUnavailable = false },
        text = { Text("Нет приложения для открытия ссылки.") },
        confirmButton = { AppActionButton("Закрыть", ActionStyle.TEXT) { linkUnavailable = false } })
}
