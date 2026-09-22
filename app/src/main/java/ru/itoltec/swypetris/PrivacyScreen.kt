package ru.itoltec.swypetris

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Показывает политику без сети; тот же исходный текст используется для публичной HTML-страницы. */
@Composable
internal fun PrivacyScreen(model: GameViewModel) {
    val context = LocalContext.current
    val paragraphs = remember(context) {
        context.assets.open("privacy.txt").bufferedReader().use { it.readText() }
            .replace("\r\n", "\n").trim().split("\n\n")
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
        Text("Конфиденциальность", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.weight(1f).testTag("privacyPage"), contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            paragraphs.forEach { paragraph -> item { Text(paragraph) } }
        }
        Button(model::contacts, Modifier.testTag("privacyBack")) { Text("Назад к контактам") }
    }
}
