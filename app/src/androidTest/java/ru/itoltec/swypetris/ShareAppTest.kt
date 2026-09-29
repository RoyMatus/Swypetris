package ru.itoltec.swypetris

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShareAppTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun qrImageDecodesToCanonicalStorePage() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val url = context.getString(R.string.app_download_url)
        val bitmap = downloadQrBitmap(url)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(url, decoded.text)
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(0, 0))
    }

    @Test fun contactsOpenShareDialogAndSystemShareIntent() {
        val model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
        compose.runOnIdle { model.finishLaunchIntro(); model.contacts() }
        compose.onNodeWithTag("contactsPage").performScrollToNode(hasTestTag("shareApp"))
        compose.onNodeWithTag("shareApp").performClick()
        compose.onNodeWithTag("downloadQr").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.app_download_url)).assertExists()
        compose.onNodeWithTag("shareDownload").assertExists()
        compose.onNodeWithTag("closeShareApp").performClick()
        compose.onNodeWithTag("downloadQr").assertDoesNotExist()

        var launched: Intent? = null
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent?) { launched = intent }
        }
        val url = compose.activity.getString(R.string.app_download_url)
        assertEquals(true, shareAppDownload(context, url))
        assertEquals(Intent.ACTION_CHOOSER, launched?.action)
        @Suppress("DEPRECATION")
        val send = launched?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals(url, send?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun apkDialogUsesInstalledVersionForQrAndActions() {
        val url = apkDownloadUrl(BuildConfig.VERSION_NAME)
        assertEquals(
            "https://github.com/RoyMatus/Swypetris/releases/download/v1.1.0/Swypetris-1.1.0.apk",
            apkDownloadUrl("1.1.0")
        )
        val bitmap = downloadQrBitmap(url)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(
            RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals(url, decoded.text)

        val model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
        compose.runOnIdle { model.finishLaunchIntro(); model.contacts() }
        compose.onNodeWithTag("contactsPage").performScrollToNode(hasTestTag("downloadApk"))
        compose.onNodeWithTag("downloadApk").performClick()
        compose.onNodeWithTag("apkQr").assertIsDisplayed()
        compose.onNodeWithText(url).assertExists()
        compose.onNodeWithTag("openApk").assertExists()
        compose.onNodeWithTag("shareApk").assertExists()
        compose.onNodeWithTag("closeApk").performClick()
        compose.onNodeWithTag("apkQr").assertDoesNotExist()

        var launched: Intent? = null
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent?) { launched = intent }
        }
        assertEquals(true, openAppDownload(context, url))
        assertEquals(Intent.ACTION_VIEW, launched?.action)
        assertEquals(url, launched?.dataString)
        assertEquals(true, shareAppDownload(context, url))
        assertEquals(Intent.ACTION_CHOOSER, launched?.action)
        @Suppress("DEPRECATION")
        val send = launched?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(url, send?.getStringExtra(Intent.EXTRA_TEXT))
    }
}
