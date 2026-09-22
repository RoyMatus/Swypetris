package ru.itoltec.swypetris

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Выбор музыки одновременно служит прослушиванием без отдельной кнопки запуска. */
@Composable
internal fun MusicPicker(model: GameViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Text("Музыка", style = MaterialTheme.typography.titleLarge)
    Box {
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth().testTag("musicPicker")) {
            Text(model.musicSelection.title, Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, Modifier.heightIn(max = 360.dp)) {
            MusicSelection.all.forEach { selection ->
                DropdownMenuItem(text = { Text(selection.title) }, modifier = Modifier.testTag("music_${selection.id}"),
                    onClick = { model.chooseMusic(selection); expanded = false })
            }
        }
    }
    val description = when (model.musicSelection) {
        MusicSelection.Off -> "Музыка и фанфары выключены."
        MusicSelection.ShuffleAll -> "Каждый круг: сначала «Коробейники», затем остальные семь в случайном порядке. Звучат здесь и в игре."
        is MusicSelection.Track -> "Повтор выбранной песни. Звучит здесь и в игре."
    }
    Text(description,
        style = MaterialTheme.typography.bodyMedium, color = LocalGamePalette.current.muted)
}
