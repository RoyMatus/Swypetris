package ru.itoltec.swypetris

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Compact menu action with its label, accent color, test tag, and click handler. */
internal data class MenuAction(val label: String, val color: Color, val tag: String,
    val icon: ImageVector, val action: () -> Unit)
