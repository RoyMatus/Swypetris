package ru.itoltec.swypetris

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun UpdateDeliveryDialog(delivery: UpdateDelivery, model: GameViewModel, recheck: () -> Unit) {
    if (!delivery.showNotice) return
    val notice = delivery.notice ?: return
    AlertDialog(onDismissRequest = {
        if (notice !is DeliveryNotice.Installing && notice !is DeliveryNotice.Downloading) delivery.dismiss()
    }, title = { Text("Обновление Swypetris") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (notice) {
                is DeliveryNotice.Downloading -> {
                    Text("Загрузка: ${notice.received * 100 / notice.total.coerceAtLeast(1)} %")
                    LinearProgressIndicator(progress = { notice.received.toFloat() / notice.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().testTag("updateDownloadProgress"))
                }
                is DeliveryNotice.Ready -> Text("Версия ${notice.update.versionName} загружена и проверена. Партия и настройки сохранятся. Установка может закрыть приложение.")
                DeliveryNotice.Permission -> Text("Для обновления разрешите Android установку из Swypetris. Это разрешение используется только для подписанных обновлений игры. Можно отказаться и продолжить игру.")
                DeliveryNotice.Installing -> Text("Сохраняем партию и устанавливаем обновление.")
                DeliveryNotice.Confirmation -> Text("Android требует подтверждения установки. Откройте системное окно, чтобы подтвердить обновление или отменить его.")
                is DeliveryNotice.Failed -> Text(notice.message)
            }
        }
    }, confirmButton = {
        when (notice) {
            is DeliveryNotice.Downloading -> TextButton(onClick = delivery::cancelDownload,
                modifier = Modifier.testTag("cancelUpdateDownload")) { Text("Отменить загрузку") }
            is DeliveryNotice.Ready -> TextButton(onClick = { delivery.install(model, true) },
                modifier = Modifier.testTag("installUpdate")) { Text("Установить") }
            DeliveryNotice.Permission -> TextButton(onClick = delivery::requestPermission,
                modifier = Modifier.testTag("updateInstallPermission")) { Text("Открыть настройки") }
            DeliveryNotice.Confirmation -> TextButton(onClick = delivery::confirmInstallation,
                modifier = Modifier.testTag("confirmAndroidInstall")) { Text("Подтвердить в Android") }
            is DeliveryNotice.Failed -> TextButton(onClick = {
                delivery.dismiss()
                if (!delivery.retry()) recheck()
            }, modifier = Modifier.testTag("retryUpdate")) { Text("Повторить") }
            DeliveryNotice.Installing -> Unit
        }
    }, dismissButton = {
        if (notice is DeliveryNotice.Ready || notice is DeliveryNotice.Permission || notice is DeliveryNotice.Failed)
            TextButton(onClick = delivery::dismiss, modifier = Modifier.testTag("postponeUpdate")) { Text("Позже") }
    })
}

@Composable
internal fun AutomaticUpdateConsentDialog(updates: AppUpdates) {
    AlertDialog(onDismissRequest = updates::postponeConsent,
        title = { Text("Автоматические обновления") },
        text = { Text("Разрешить автоматическую загрузку и установку новых версий Swypetris? Загрузка использует интернет. Установка начнётся только в главном меню после сохранения партии и может закрыть приложение. Android может запросить разрешение и подтверждение. Режим можно отключить в настройках.",
            modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = { updates.answerConsent(true) },
                modifier = Modifier.testTag("allowAutomaticUpdates")) { Text("Разрешить") }
        }, dismissButton = {
            Column {
                TextButton(onClick = updates::postponeConsent,
                    modifier = Modifier.testTag("laterAutomaticUpdates")) { Text("Позже") }
                TextButton(onClick = { updates.answerConsent(false) },
                    modifier = Modifier.testTag("manualUpdatesOnly")) { Text("Только вручную") }
            }
        })
}
