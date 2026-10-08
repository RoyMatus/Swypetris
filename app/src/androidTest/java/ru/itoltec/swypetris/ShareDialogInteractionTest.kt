package ru.itoltec.swypetris

import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShareDialogInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shareButtonUsesQrLinkWithoutTriggeringBackgroundDismiss() {
        var shown by mutableStateOf(true)
        var launched: Intent? = null
        compose.setContent {
            val base = LocalContext.current
            val context = remember(base) { object : ContextWrapper(base) {
                override fun startActivity(intent: Intent?) { launched = intent }
            } }
            CompositionLocalProvider(LocalContext provides context) {
                if (shown) ApkDownloadDialog { shown = false }
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(1)
        compose.onNodeWithTag("shareApk").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(shown)
            assertEquals(Intent.ACTION_CHOOSER, launched?.action)
            @Suppress("DEPRECATION")
            val send = launched?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertEquals(apkDownloadUrl(), send?.getStringExtra(Intent.EXTRA_TEXT))
        }
        compose.onNodeWithTag("apkQr").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertFalse(shown) }
    }

    @Test fun singleShareActionRemainsAccessibleAtLargeFont() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                ShareAppDialog {}
            }
        }
        val qr = compose.onNodeWithTag("downloadQr").fetchSemanticsNode().boundsInRoot
        assertEquals(qr.width, qr.height, 1f)
        compose.onNodeWithTag("shareDownload").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(1)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        java.io.File(context.getExternalFilesDir(null), "share-dialog-large-font.png").outputStream().use {
            requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .uiAutomation.takeScreenshot()).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
