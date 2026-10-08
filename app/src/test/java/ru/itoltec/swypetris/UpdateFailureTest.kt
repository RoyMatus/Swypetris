package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
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
        for (case in listOf(
            StreamCase(bytes.copyOf(2), 3L, hash, UpdateFailureReason.INCOMPLETE),
            StreamCase(bytes, 2L, hash, UpdateFailureReason.INTEGRITY),
            StreamCase(bytes, 3L, "0".repeat(64), UpdateFailureReason.INTEGRITY))) {
            val file = File.createTempFile("update-failure", ".apk")
            try {
                val failure = assertThrows(UpdateFailure::class.java) {
                    copyUpdate(ByteArrayInputStream(case.content), file, case.size, case.digest, {}, {})
                }
                assertEquals(case.reason, failure.reason)
                assertFalse(file.exists())
            } finally { file.delete() }
        }
    }

    @Test fun expectedIoFailureIsReportedButCancellationKeepsCleanupAndIdentity() {
        val reported = runBlocking {
            recoverExpectedUpdateFailure({ throw IOException("disk write") }) {
                updateFailureMessage(it, UpdateStage.SAVE)
            }
        }
        assertEquals(UpdateFailureReason.PERSISTENCE.userMessage, reported)
        val cancellation = CancellationException("activity destroyed")
        var recovered = false
        var cleaned = false
        val actual = assertThrows(CancellationException::class.java) {
            runBlocking {
                try {
                    recoverExpectedUpdateFailure({ throw cancellation }) { recovered = true }
                } finally { cleaned = true }
            }
        }
        assertSame(cancellation, actual)
        assertFalse(recovered)
        org.junit.Assert.assertTrue(cleaned)
    }

    @Test fun programmingFailureDoesNotBecomeARecoverableUpdateNotice() {
        val failure = NullPointerException("unexpected state")
        var recovered = false
        val actual = assertThrows(NullPointerException::class.java) {
            runBlocking {
                recoverExpectedUpdateFailure({ throw failure }) { recovered = true }
            }
        }
        assertSame(failure, actual)
        assertFalse(recovered)
    }
}
