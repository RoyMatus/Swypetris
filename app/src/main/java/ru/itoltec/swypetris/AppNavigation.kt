package ru.itoltec.swypetris

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun AppScreen(model: GameViewModel, updates: AppUpdates?, onExit: () -> Unit) {
    if (model.screen == GameScreen.MENU) MainMenu(model, onExit, onCheckUpdates = { updates?.check(true) })
    else if (model.screen == GameScreen.CONTACTS) ContactsScreen(model)
    else if (model.screen == GameScreen.PRIVACY) PrivacyScreen()
    else if (model.screen == GameScreen.LEGAL) LegalScreen()
    else if (model.screen == GameScreen.HELP) HelpScreen()
    else if (model.screen == GameScreen.SETTINGS) SettingsScreen(model,
        onCheckUpdates = { updates?.check(true) }, automaticUpdates = updates?.automaticEnabled == true,
        onAutomaticUpdatesChange = { updates?.requestAutomatic(it) })
    else if (model.screen == GameScreen.VICTORY) VictoryScreen(model)
    else if (model.screen == GameScreen.RECORD) RecordScreen(model)
    else if (model.screen in listOf(GameScreen.GAME_OVER, GameScreen.RESULTS)) ResultsScreen(model)
    else model.game?.let { GameContent(model, it) }
}

@Composable
internal fun AppUpdateDialogs(model: GameViewModel, updates: AppUpdates) {
    if (updates.consentRequested) AutomaticUpdateConsentDialog(updates)
    else UpdateDeliveryDialog(updates.delivery, model) { updates.check(true) }
    if (!updates.consentRequested && updates.delivery.notice == null) updates.notice?.let { notice ->
        AlertDialog(onDismissRequest = { if (notice != UpdateNotice.StoreInstalling) updates.dismiss() },
            title = { Text(if (notice is UpdateNotice.Available) "Доступно обновление" else "Проверка обновлений") },
            text = { Text(updateNoticeMessage(notice)) },
            confirmButton = {
                if (notice is UpdateNotice.Available) TextButton(onClick = { updates.open(notice.update) },
                    modifier = Modifier.testTag("confirmUpdate")) { Text("Обновить") }
                else if (notice is UpdateNotice.StoreReady) TextButton(onClick = { updates
                    .installStore(model) }) { Text("Установить") }
                else if (notice != UpdateNotice.StoreInstalling) TextButton(onClick = updates::dismiss) {
                    Text("Понятно") }
            },
            dismissButton = if (notice is UpdateNotice.Available || notice is UpdateNotice.StoreReady) ({
                TextButton(onClick = updates::dismiss, modifier = Modifier.testTag("laterUpdate")) { Text("Позже") }
            }) else null)
    }

}

private fun updateNoticeMessage(notice: UpdateNotice): String =
    when (notice) {
    is UpdateNotice.Available -> "Установлена версия ${BuildConfig.VERSION_NAME}. Доступна " +
        "${notice.update.versionName}."
    is UpdateNotice.StoreReady -> "RuStore загрузил версию ${notice.update.versionName}. Установка " +
        "сохранит партию и перезапустит приложение."
    UpdateNotice.StoreInstalling -> "Сохраняем партию. RuStore устанавливает обновление."
    UpdateNotice.Current -> "Установлена актуальная версия ${BuildConfig.VERSION_NAME}."
    UpdateNotice.Failed -> "Не удалось завершить обновление. Проверьте подключение и повторите " +
        "проверку позже."
    is UpdateNotice.RateLimited -> "GitHub временно ограничил проверку обновлений. Повторить можно после " +
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM)
                    .format(java.util.Date(notice.retryAtMillis)) + " (время устройства). Игру можно продолжить."
    }
