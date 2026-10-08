package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class UpdateFailureTest {
    private data class StreamCase(val content: ByteArray, val size: Long, val digest: String,
        val reason: UpdateFailureReason)
    @Test fun validationReasonsOverrideStageWithoutExposingDiagnostics() {
        for (reason in UpdateFailureReason.entries) {
            val failure = UpdateFailure(reason, "private diagnostic")
            assertEquals(reason.userMessage, updateFailureMessage(failure, UpdateStage.DOWNLOAD))
            assertFalse(updateFailureMessage(failure, UpdateStage.DOWNLOAD).contains("private diagnostic"))
        }
        assertEquals(UpdateFailureReason.NETWORK.userMessage,
            updateFailureMessage(IOException("HTTP 500"), UpdateStage.CONNECT))
        assertEquals(UpdateFailureReason.PERSISTENCE.userMessage,
            updateFailureMessage(IOException("disk write"), UpdateStage.SAVE))
    }

    @Test fun streamFailuresRemainDistinctAndRemoveInvalidFiles() {
        val bytes = byteArrayOf(1, 2, 3)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        for ((content, size, digest, reason) in listOf(
            StreamCase(bytes.copyOf(2), 3L, hash, UpdateFailureReason.INCOMPLETE),
            StreamCase(bytes, 2L, hash, UpdateFailureReason.INTEGRITY),
            StreamCase(bytes, 3L, "0".repeat(64), UpdateFailureReason.INTEGRITY))) {
            val file = File.createTempFile("update-failure", ".apk")
            try {
                val failure = assertThrows(UpdateFailure::class.java) {
                    copyUpdate(ByteArrayInputStream(content), file, size, digest, {}, {})
                }
                assertEquals(reason, failure.reason)
                assertFalse(file.exists())
            } finally { file.delete() }
        }
    }
}
