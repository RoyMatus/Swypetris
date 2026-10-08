package ru.itoltec.swypetris

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Shows the offline privacy policy; the same source text feeds the public HTML page. */
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
    }
}
