package ru.itoltec.swypetris

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/** The encoder's four-module margin remains part of the white image in every theme. */
internal fun downloadQrMatrix(url: String): BitMatrix = QRCodeWriter().encode(
    url, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.MARGIN to 4)
)

/** Renders at an integer scale so the modules stay crisp when Compose resizes the image. */
internal fun downloadQrBitmap(url: String): Bitmap {
    val matrix = downloadQrMatrix(url)
    val scale = 16
    val width = matrix.width * scale
    val pixels = IntArray(width * width) { android.graphics.Color.WHITE }
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
        if (!matrix[x, y]) continue
        for (py in y * scale until (y + 1) * scale) {
            java.util.Arrays.fill(pixels, py * width + x * scale,
                py * width + (x + 1) * scale, android.graphics.Color.BLACK)
        }
    }
    return Bitmap.createBitmap(pixels, width, width, Bitmap.Config.RGB_565)
}

internal fun openAppDownload(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

internal fun shareAppDownload(context: Context, url: String): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, "Поделиться Swypetris"))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** The latest published GitHub Release provides the APK for every new app build. */
internal fun apkDownloadUrl(): String =
    "https://github.com/RoyMatus/Swypetris/releases/latest/download/Swypetris.apk"

/** Shows the canonical store link, a scannable QR code, and Android sharing actions. */
@Composable
internal fun ShareAppDialog(onDismiss: () -> Unit) = DownloadLinkDialog(onDismiss, directApk = false)

@Composable
internal fun ApkDownloadDialog(onDismiss: () -> Unit) = DownloadLinkDialog(onDismiss, directApk = true)

@Composable
private fun DownloadLinkDialog(onDismiss: () -> Unit, directApk: Boolean) {
    val context = LocalContext.current
    val url = if (directApk) apkDownloadUrl()
        else context.getString(R.string.app_download_url)
    val qr = remember(url) { downloadQrBitmap(url).asImageBitmap() }
    var actionUnavailable by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (directApk) "Скачать APK Swypetris" else "Поделиться Swypetris") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (directApk) "Отсканируйте код, чтобы скачать APK напрямую"
                    else "Отсканируйте код, чтобы скачать Swypetris")
                Image(qr, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .testTag(if (directApk) "apkQr" else "downloadQr")
                        .semantics { contentDescription = if (directApk) "QR-код прямой загрузки APK Swypetris"
                            else "QR-код ссылки на Swypetris в RuStore" },
                    filterQuality = FilterQuality.None)
                Text(url, style = MaterialTheme.typography.bodySmall)
                AppActionButton(if (directApk) "Открыть загрузку APK" else "Открыть RuStore", ActionStyle.SECONDARY,
                    Modifier.fillMaxWidth().testTag(if (directApk) "openApk" else "openDownload")) {
                    actionUnavailable = !openAppDownload(context, url)
                }
                AppActionButton("Поделиться ссылкой", ActionStyle.SECONDARY,
                    Modifier.fillMaxWidth().testTag(if (directApk) "shareApk" else "shareDownload")) {
                    actionUnavailable = !shareAppDownload(context, url)
                }
                if (actionUnavailable) Text("Нет приложения для открытия ссылки или передачи её другому пользователю.",
                    color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            AppActionButton("Закрыть", ActionStyle.TEXT,
                Modifier.testTag(if (directApk) "closeApk" else "closeShareApp"),
                onClick = onDismiss)
        })
}
