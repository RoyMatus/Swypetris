package ru.itoltec.swypetris

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.logging.Logger

internal const val MAX_UPDATE_BYTES = 256L * 1024 * 1024
private const val MAX_CONNECTION_ATTEMPTS = 6
private const val CONNECTION_TIMEOUT_MS = 10_000
private const val MAX_ERROR_RESPONSE_BYTES = 16 * 1024
private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
internal class UpdateRateLimitException(val retryAtMillis: Long) : IOException("GitHub request limit reached")

/** Report failed cleanup without masking a download failure or cancellation. Recovery retries it. */
internal fun removeUpdateFile(file: File) {
    if (file.exists() && !file.delete())
        Logger.getLogger("SwypetrisUpdates").warning("Cannot remove update file: ${file.name}")
}

/** A partial or mismatched download is never exposed as an installable APK. */
internal fun copyUpdate(input: InputStream, destination: File, expectedSize: Long,
    expectedSha256: String, checkActive: () -> Unit, progress: (Long) -> Unit) {
    require(expectedSize in 1..MAX_UPDATE_BYTES)
    require(Regex("[a-fA-F0-9]{64}").matches(expectedSha256))
    val digest = MessageDigest.getInstance("SHA-256")
    var complete = false
    try {
        val stream = UpdateFileWrites.open(destination)
        stream.use { output ->
            val buffer = ByteArray(64 * 1024)
            var received = 0L
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                received += count
                validateDownloadBound(received, expectedSize)
                UpdateFileWrites.write(output, buffer, count)
                digest.update(buffer, 0, count)
                progress(received)
            }
            checkActive()
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            validateCompleteDownload(received, expectedSize, hash, expectedSha256)
            UpdateFileWrites.flush(output)
        }
        complete = true
    } finally {
        if (!complete) removeUpdateFile(destination)
    }
}

/** GitHub assets redirect to its asset CDN; every hop must remain HTTPS. */
internal fun openUpdateConnection(address: String, accept: String = "application/octet-stream",
    connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    clock: () -> Long = System::currentTimeMillis): HttpURLConnection {
    var url = URL(address)
    repeat(MAX_CONNECTION_ATTEMPTS) {
        validateUpdateAddress(url)
        val connection = connectionFactory(url)
        connection.instanceFollowRedirects = false
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = CONNECTION_TIMEOUT_MS
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("Accept-Encoding", "identity")
        val status = try { connection.responseCode } catch (failure: IOException) {
            connection.disconnect()
            throw failure
        }
        if (status == HttpURLConnection.HTTP_OK) return connection
        if (status in REDIRECT_STATUSES) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            url = updateRedirect(url, location)
        } else {
            try {
                throwUpdateResponse(connection, status, clock())
            } finally { connection.disconnect() }
        }
    }
    throw IOException("Too many update download redirects")
}

private fun throwUpdateResponse(connection: HttpURLConnection, status: Int, now: Long): Nothing {
    val message = readUpdateError(connection)
    throw githubRateLimit(status, connection.getHeaderField("x-ratelimit-remaining"),
        connection.getHeaderField("Retry-After"), connection.getHeaderField("x-ratelimit-reset"),
        message, now) ?: IOException("Update server returned HTTP $status")
}

/** Error details are diagnostic only; a missing/oversized body must not override response headers. */
private fun readUpdateError(connection: HttpURLConnection): String = try {
    connection.errorStream?.use { it.readBytesLimited(MAX_ERROR_RESPONSE_BYTES).toString(Charsets.UTF_8) } ?: ""
} catch (_: IOException) { "" }

internal fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > limit) throw IOException("Update metadata is too large")
        output.write(buffer, 0, count)
    }
}

private object UpdateFileWrites {
    fun open(destination: File): java.io.FileOutputStream = try { destination.outputStream() }
    catch (failure: IOException) {
        throw UpdateFailure(UpdateFailureReason.PERSISTENCE, "Cannot open update file", failure)
    }

    fun write(output: java.io.FileOutputStream, buffer: ByteArray, count: Int) {
        try { output.write(buffer, 0, count) } catch (failure: IOException) {
            throw UpdateFailure(UpdateFailureReason.PERSISTENCE, "Cannot write update file", failure)
        }
    }

    fun flush(output: java.io.FileOutputStream) {
        try { output.fd.sync() } catch (failure: IOException) {
            throw UpdateFailure(UpdateFailureReason.PERSISTENCE, "Cannot flush update file", failure)
        }
    }
}

private fun validateDownloadBound(received: Long, expectedSize: Long) {
    if (received > expectedSize) throw UpdateFailure(
        UpdateFailureReason.INTEGRITY, "APK size exceeds release metadata")
}

private fun validateCompleteDownload(received: Long, expectedSize: Long, hash: String, expectedSha256: String) {
    if (received != expectedSize) throw UpdateFailure(
        UpdateFailureReason.INCOMPLETE, "APK download is incomplete")
    if (!hash.equals(expectedSha256, true)) throw UpdateFailure(
        UpdateFailureReason.INTEGRITY, "APK hash does not match release metadata")
}

private fun validateUpdateAddress(url: URL) {
    if (url.protocol != "https" || url.userInfo != null ||
        url.host !in setOf("github.com", "api.github.com", "release-assets.githubusercontent.com",
        "objects.githubusercontent.com")) throw IOException("Unexpected update download address")
}

private fun updateRedirect(url: URL, location: String?): URL {
    if (location == null) throw IOException("Missing update redirect address")
    return URL(url, location)
}
