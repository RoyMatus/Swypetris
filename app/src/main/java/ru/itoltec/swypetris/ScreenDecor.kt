package ru.itoltec.swypetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact day/night artwork shared by the non-gameplay screens. */
@Composable
internal fun ScreenArtHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    val palette = LocalGamePalette.current
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val art = ImageBitmap.imageResource(if (palette.light) R.drawable.settings_day_header
        else R.drawable.settings_synthwave_header)
    Box(Modifier.fillMaxWidth().heightIn(min = 90.dp + statusHeight)) {
        Image(art, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(
            palette.background.copy(alpha = if (palette.light) .16f else .34f), Color.Transparent))))
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = statusHeight + 8.dp,
            end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                OutlinedIconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("settingsBack"),
                    shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, palette.accent),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        containerColor = palette.background.copy(alpha = .75f),
                        contentColor = if (palette.light) palette.text else Color.White)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Вернуться в меню")
                }
                Spacer(Modifier.width(18.dp))
            }
            Column {
                Text(title, color = if (palette.light) palette.text else Color.White,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                    fontSize = 24.sp, maxLines = 2)
                Text(subtitle, color = if (palette.light) palette.text else Color(0xFFADB9E3),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
        }
    }
}

/** Theme-aware surface matching the bordered cards in Settings. */
@Composable
internal fun ThemedCard(accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalGamePalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.background(Brush.linearGradient(listOf(
        lerp(palette.panel, accent, if (palette.light) .045f else .09f), palette.panel)), shape)
        .border(1.dp, accent, shape)) { content() }
}
