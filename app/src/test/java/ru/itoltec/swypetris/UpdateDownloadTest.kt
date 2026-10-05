package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class UpdateDownloadTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = ByteArray(150_000) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

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
}
