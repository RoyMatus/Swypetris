package ru.itoltec.swypetris

import android.content.Context
import android.content.pm.PackageManager
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.PatternMatcher
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class StoreComplianceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun distributedApplicationCannotRequestPackageInstallation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertFalse("RuStore rejected this permission", "android.permission.REQUEST_INSTALL_PACKAGES" in permissions)
        assertFalse("The game must not install packages",
            "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION" in permissions)
    }

    @Test fun githubUpdateOpensReleasePageWithoutDownloadingAnApk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
            addDataAuthority("github.com", null)
            addDataPath("/RoyMatus/Swypetris/releases/tag/v9.9.9", PatternMatcher.PATTERN_LITERAL)
        }
        val monitor = Instrumentation.ActivityMonitor(
            filter, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        instrumentation.addMonitor(monitor)
        try {
            compose.runOnIdle {
                val updates = AppUpdates(compose.activity)
                try {
                    updates.open(AvailableUpdate(Long.MAX_VALUE, "9.9.9",
                        "https://example.invalid/untrusted.apk", false))
                    assertEquals("Expected the exact GitHub release page", 1, monitor.hits)
                    assertFalse(java.io.File(compose.activity.noBackupFilesDir, "updates/ready.apk").exists())
                } finally { updates.close() }
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
