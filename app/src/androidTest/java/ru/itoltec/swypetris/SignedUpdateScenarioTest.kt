package ru.itoltec.swypetris

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in release integrity fixture; the distributed app no longer installs APKs. */
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
        runBlocking {
            withContext(Dispatchers.IO) {
                val update = requireNotNull(parseGitHubRelease(
                    args.getString("signedReleaseJson")?.let { JSONObject(File(it).readText()) }
                        ?: json("https://api.github.com/repos/RoyMatus/Swypetris/releases/tags/v$version"),
                    json("https://github.com/RoyMatus/Swypetris/releases/download/v$version/update.json")))
                val file = File(compose.activity.cacheDir, "release-integrity.apk")
                val connection = openUpdateConnection(requireNotNull(update.apkUrl))
                try {
                    connection.inputStream.use {
                        copyUpdate(it, file, update.sizeBytes, requireNotNull(update.sha256), {}, {})
                    }
                    val result = runCatching { validateUpdateApk(compose.activity, file, update) }
                    if (args.getString("expectSignerMismatch") == "true") {
                        assertEquals(UpdateFailureReason.SIGNER, (result.exceptionOrNull() as UpdateFailure).reason)
                    } else {
                        result.getOrThrow()
                        assertTrue(file.isFile)
                    }
                } finally {
                    connection.disconnect()
                    removeUpdateFile(file)
                }
            }
        }
    }
}
