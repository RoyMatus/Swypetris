package ru.itoltec.swypetris

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.res.stringResource

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MenuAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null) {
    val skin = LocalMenuSkin.current
    if (skin == null) {
        AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text)
    } else {
        val paneDescription = stringResource(androidx.compose.material3.R.string.m3c_dialog)
        Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false)) {
            val window = (LocalView.current.parent as DialogWindowProvider).window
            val originalDim = remember(window) { window.attributes.dimAmount }
            SideEffect { window.setDimAmount(0f) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val scrimSource = remember { MutableInteractionSource() }
                Box(Modifier.fillMaxSize().background(skin.scrim.copy(alpha = originalDim))
                    .clickable(scrimSource, indication = null, onClick = onDismissRequest))
                Column(Modifier.padding(horizontal = 24.dp).then(modifier).widthIn(min = 280.dp, max = 560.dp)
                    .semantics { paneTitle = paneDescription }
                    .heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 48.dp)
                    .menuSurfaceArt("dialog").pointerInput(Unit) { detectTapGestures { } }
                    .padding(24.dp)) {
                    CompositionLocalProvider(LocalContentColor provides skin.text) {
                        icon?.let {
                            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) {
                                CompositionLocalProvider(LocalContentColor provides skin.control) { it() }
                            }
                        }
                        title?.let {
                            Box(Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                                .padding(bottom = 16.dp)) {
                                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() }
                            }
                        }
                        text?.let {
                            Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                            }
                        }
                        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                            FlowRow(Modifier.align(Alignment.End),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                dismissButton?.invoke()
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A custom menu dialog keeps its caller-owned content and native dismissal/focus. */
@Composable
internal fun MenuDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val skin = LocalMenuSkin.current
    if (skin == null) {
        Dialog(onDismissRequest, content = content)
        return
    }
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        val originalDim = remember(window) { window.attributes.dimAmount }
        SideEffect { window.setDimAmount(0f) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val source = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxSize().background(skin.scrim.copy(alpha = originalDim))
                .clickable(source, indication = null, onClick = onDismissRequest))
            Box(Modifier.padding(horizontal = 24.dp).widthIn(min = 280.dp, max = 560.dp)
                .pointerInput(Unit) { detectTapGestures { } }) { content() }
        }
    }
}
