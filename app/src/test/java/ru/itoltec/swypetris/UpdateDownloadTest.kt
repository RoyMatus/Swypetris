package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.net.HttpURLConnection
import java.net.URL

class UpdateDownloadTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = ByteArray(150_000) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class Response(private val status: Int, private val headers: Map<String, String> = emptyMap(),
        private val error: String = "") : HttpURLConnection(URL("https://api.github.com")) {
        var disconnected = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String): String? {
            check(!disconnected) { "Headers read after disconnect" }
            return headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        }
        override fun getErrorStream() = ByteArrayInputStream(error.toByteArray())
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
    }

    @Test fun controlledHttpErrorsUseHeadersAndDetailsBeforeDisconnecting() {
        val now = 1_700_000_000_000L
        val generic = Response(403, error = "{\"message\":\"Forbidden\"}")
        val denied = assertThrows(IOException::class.java) {
            openUpdateConnection("https://api.github.com/repos/RoyMatus/Swypetris/releases/latest",
                connectionFactory = { generic }, clock = { now })
        }
        assertFalse(denied is UpdateRateLimitException)
        assertTrue(generic.disconnected)
        for (response in listOf(Response(403, mapOf("x-ratelimit-remaining" to "0", "Retry-After" to "120")),
            Response(429, mapOf("Retry-After" to "120")),
            Response(403, error = "{\"message\":\"You have exceeded a secondary rate limit.\"}"))) {
            val failure = assertThrows(UpdateRateLimitException::class.java) {
                openUpdateConnection("https://api.github.com/repos/RoyMatus/Swypetris/releases/latest",
                    connectionFactory = { response }, clock = { now })
            }
            assertTrue(failure.retryAtMillis in (now + 60_000)..(now + 120_000))
            assertTrue(response.disconnected)
        }
    }

    @Test fun redirectsCannotEscapeTrustedHttpsHosts() {
        val response = Response(302, mapOf("Location" to "https://example.com/Swypetris.apk"))
        var connections = 0
        assertThrows(IOException::class.java) {
            openUpdateConnection("https://github.com/RoyMatus/Swypetris/releases/latest",
                connectionFactory = { connections++; response })
        }
        assertEquals(1, connections)
        assertTrue(response.disconnected)
    }

    @Test fun boundedMetadataAndOversizedErrorBodiesPreserveSecurityAndHeaderEvidence() {
        assertThrows(IOException::class.java) { ByteArrayInputStream(ByteArray(257)).readBytesLimited(256) }
        val response = Response(403, mapOf("x-ratelimit-remaining" to "0"), "x".repeat(20 * 1024))
        assertThrows(UpdateRateLimitException::class.java) {
            openUpdateConnection("https://api.github.com/repos/RoyMatus/Swypetris/releases/latest",
                connectionFactory = { response }, clock = { 1000L })
        }
        assertTrue(response.disconnected)
    }

    @Test fun completeDownloadMatchesMetadataAndReportsProgress() {
        val file = folder.newFile()
        var received = 0L
        copyUpdate(ByteArrayInputStream(bytes), file, bytes.size.toLong(), hash, {}, { received = it })
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), received)
    }

    @Test fun incompleteOversizedAndCorruptedDownloadsAreDeleted() {
        for ((content, size, digest) in listOf(
            Triple(bytes.copyOf(10), bytes.size.toLong(), hash),
            Triple(bytes, 10L, hash),
            Triple(bytes, bytes.size.toLong(), "0".repeat(64)))) {
            val file = folder.newFile()
            assertThrows(IOException::class.java) {
                copyUpdate(ByteArrayInputStream(content), file, size, digest, {}, {})
            }
            assertFalse(file.exists())
        }
    }

    @Test fun cancellationDeletesPartialApk() {
        val file = folder.newFile()
        var calls = 0
        assertThrows(CancellationException::class.java) {
            copyUpdate(ByteArrayInputStream(bytes), file, bytes.size.toLong(), hash,
                { if (++calls == 2) throw CancellationException() }, {})
        }
        assertFalse(file.exists())
    }

    @Test fun insecureOrForeignDownloadAddressesAreRejectedWithoutConnecting() {
        for (address in listOf("http://github.com/RoyMatus/Swypetris/releases/latest",
            "https://example.com/Swypetris.apk", "https://user@github.com/Swypetris.apk")) {
            assertThrows(IOException::class.java) { openUpdateConnection(address) }
        }
    }

    @Test fun failedCleanupReportsWarningWithoutReplacingCancellation() {
        val file = object : File(folder.newFile().absolutePath) {
            override fun delete() = false
        }
        val warnings = mutableListOf<String>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { warnings.add(record.message) }
            override fun flush() = Unit
            override fun close() = Unit
        }
        val logger = Logger.getLogger("SwypetrisUpdates")
        logger.addHandler(handler)
        try {
            assertThrows(CancellationException::class.java) {
                copyUpdate(ByteArrayInputStream(bytes), file, bytes.size.toLong(), hash,
                    { throw CancellationException() }, {})
            }
            assertEquals(listOf("Cannot remove update file: ${file.name}"), warnings)
        } finally { logger.removeHandler(handler) }
    }
}
