package ru.itoltec.swypetris

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape

/** Music choices also serve as a preview, without a separate play button. */
@Composable
internal fun MusicPicker(model: GameViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalGamePalette.current
    val accent = palette.piece(Tetromino.J)
    val description = when (model.musicSelection) {
        MusicSelection.Off -> "Музыка выключена"
        MusicSelection.ShuffleAll -> "Восемь песен по кругу"
        is MusicSelection.Track -> "Повтор выбранной песни"
    }
    val selectionLabel = when (model.musicSelection) {
        MusicSelection.Off -> "Выключена"
        MusicSelection.ShuffleAll -> "Включена"
        is MusicSelection.Track -> model.musicSelection.title
    }
    @Composable fun selector() {
        Box {
            OutlinedButton(onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("musicPicker"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.background.copy(alpha = .65f), contentColor = palette.text),
                border = BorderStroke(1.dp, accent)) {
                Text(selectionLabel, Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text("▾")
            }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, Modifier.heightIn(max = 360.dp)) {
            MusicSelection.all.forEach { selection ->
                DropdownMenuItem(text = { Text(selection.title) }, modifier = Modifier.testTag("music_${selection.id}"),
                    onClick = { model.chooseMusic(selection); expanded = false })
            }
        }
        }
    }
    if (LocalDensity.current.fontScale >= 1.4f) {
        SettingsLabel("Музыка", description)
        Spacer(Modifier.height(6.dp))
        selector()
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SettingsLabel("Музыка", description) }
            Box(Modifier.width(158.dp)) { selector() }
        }
    }
}
