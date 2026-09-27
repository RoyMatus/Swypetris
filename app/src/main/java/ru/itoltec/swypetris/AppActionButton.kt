package ru.itoltec.swypetris

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal enum class ActionStyle { PRIMARY, SECONDARY, TEXT }

/** Shared Material treatment for actions with the same visual priority. */
@Composable
internal fun AppActionButton(label: String, style: ActionStyle, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    val height = if (style == ActionStyle.TEXT) 48.dp else 56.dp
    val shape = RoundedCornerShape(12.dp)
    val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    when (style) {
        ActionStyle.PRIMARY -> Button(onClick, modifier.heightIn(min = height), shape = shape,
            contentPadding = contentPadding) { Text(label) }
        ActionStyle.SECONDARY -> OutlinedButton(onClick, modifier.heightIn(min = height), shape = shape,
            contentPadding = contentPadding) { Text(label) }
        ActionStyle.TEXT -> TextButton(onClick, modifier.heightIn(min = height), shape = shape,
            contentPadding = contentPadding) { Text(label) }
    }
}
