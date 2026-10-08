package ru.itoltec.swypetris

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateDeliveryRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun failedAttemptCanBeRetriedAndLeavesNoInstallableFile() {
        lateinit var delivery: UpdateDelivery
        compose.runOnIdle {
            compose.activity.getSharedPreferences("app_update_download", Context.MODE_PRIVATE).edit().clear().commit()
            delivery = UpdateDelivery(compose.activity)
        }
        compose.waitUntil(5000) {
            (UpdateDelivery::class.java.getDeclaredField("operation").apply { isAccessible = true }
                .get(delivery) as? kotlinx.coroutines.Job)?.isActive != true
        }
        try {
            val update = AvailableUpdate(Long.MAX_VALUE, "9.9.9", "https://example.invalid/Swypetris.apk",
                false, "0".repeat(64), 1)
            compose.runOnIdle { delivery.download(update) }
            compose.waitUntil(5000) { delivery.notice is DeliveryNotice.Failed }
            compose.runOnIdle {
                assertEquals(UpdateFailureReason.NETWORK.userMessage, (delivery.notice as DeliveryNotice.Failed).message)
                assertFalse(delivery.showReady())
                assertTrue(delivery.retry())
                assertTrue(delivery.notice is DeliveryNotice.Downloading)
            }
            compose.waitUntil(5000) { delivery.notice is DeliveryNotice.Failed }
            assertFalse(java.io.File(compose.activity.noBackupFilesDir, "updates/ready.apk").exists())
            assertTrue(java.io.File(compose.activity.noBackupFilesDir, "updates").listFiles().orEmpty().isEmpty())
        } finally { compose.runOnIdle { delivery.close() } }
    }
}
