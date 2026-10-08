package ru.itoltec.swypetris

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in live GitHub fixture. The shell verifies the installed version after process replacement. */
class SignedUpdateScenarioTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun liveGitHubDownloadChecksSignerBeforeInstaller() {
        val args = InstrumentationRegistry.getArguments()
        val version = args.getString("signedUpdateVersion")
        assumeTrue("Explicit signed-update fixture required", version != null)
        fun json(url: String): JSONObject {
            val connection = openUpdateConnection(url, "application/vnd.github+json")
            return try {
                JSONObject(connection.inputStream.use { it.readBytesLimited(256 * 1024) }.toString(Charsets.UTF_8))
            } finally { connection.disconnect() }
        }
        val update = runBlocking {
            withContext(Dispatchers.IO) {
                requireNotNull(parseGitHubRelease(
                    args.getString("signedReleaseJson")?.let { JSONObject(java.io.File(it).readText()) }
                        ?: json("https://api.github.com/repos/RoyMatus/Swypetris/releases/tags/v$version"),
                    json("https://github.com/RoyMatus/Swypetris/releases/download/v$version/update.json")))
            }
        }
        lateinit var delivery: UpdateDelivery
        compose.runOnIdle { delivery = UpdateDelivery(compose.activity) }
        compose.waitUntil(5000) {
            (UpdateDelivery::class.java.getDeclaredField("operation").apply { isAccessible = true }
                .get(delivery) as? kotlinx.coroutines.Job)?.isActive != true
        }
        try {
            compose.runOnIdle { delivery.download(update) }
            compose.waitUntil(180_000) { delivery.notice is DeliveryNotice.Ready ||
                delivery.notice is DeliveryNotice.Failed }
            if (args.getString("expectSignerMismatch") == "true") {
                assertEquals(UpdateFailureReason.SIGNER.userMessage, (delivery.notice as DeliveryNotice.Failed).message)
                assertFalse(java.io.File(compose.activity.noBackupFilesDir, "updates/ready.apk").exists())
            } else {
                assertTrue("Download result: ${delivery.notice}", delivery.notice is DeliveryNotice.Ready)
                if (args.getString("commitSignedUpdate") == "true") {
                    compose.runOnIdle {
                        delivery.install(androidx.lifecycle
                            .ViewModelProvider(compose.activity)[GameViewModel::class.java], true)
                    }
                    compose.waitUntil(30_000) {
                        delivery.notice == DeliveryNotice.Confirmation || delivery.notice is DeliveryNotice.Failed ||
                            delivery.notice == null
                    }
                    assertFalse("Installer rejected update: ${delivery.notice}",
                        delivery.notice is DeliveryNotice.Failed)
                    compose.runOnIdle { delivery.confirmInstallation() }
                }
            }
        } finally { compose.runOnIdle { delivery.close() } }
    }
}
