package ru.itoltec.swypetris

import android.content.Context
import android.content.pm.PackageManager
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
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
        var opened: Intent? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
                opened = intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.runOnIdle {
                val updates = AppUpdates(compose.activity)
                try {
                    updates.open(AvailableUpdate(Long.MAX_VALUE, "9.9.9",
                        "https://example.invalid/untrusted.apk", false))
                    assertEquals(Intent.ACTION_VIEW, opened?.action)
                    assertEquals("https://github.com/RoyMatus/Swypetris/releases/tag/v9.9.9",
                        opened?.dataString)
                    assertFalse(java.io.File(compose.activity.noBackupFilesDir, "updates/ready.apk").exists())
                } finally { updates.close() }
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
