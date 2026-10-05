package ru.itoltec.swypetris

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.logging.Logger

internal const val MAX_UPDATE_BYTES = 256L * 1024 * 1024
internal class UpdateRateLimitException : IOException("GitHub request limit reached")

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
    try {
        destination.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var received = 0L
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                received += count
                if (received > expectedSize) throw IOException("APK size exceeds release metadata")
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                progress(received)
            }
            checkActive()
            if (received != expectedSize) throw IOException("APK download is incomplete")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hash.equals(expectedSha256, true)) throw IOException("APK hash does not match release metadata")
            output.fd.sync()
        }
    } catch (failure: Throwable) {
        removeUpdateFile(destination)
        throw failure
    }
}

/** GitHub assets redirect to its asset CDN; every hop must remain HTTPS. */
internal fun openUpdateConnection(address: String, accept: String = "application/octet-stream"): HttpURLConnection {
    var url = URL(address)
    repeat(6) {
        if (url.protocol != "https" || url.userInfo != null ||
            url.host !in setOf("github.com", "api.github.com", "release-assets.githubusercontent.com",
                "objects.githubusercontent.com")) throw IOException("Unexpected update download address")
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("Accept-Encoding", "identity")
        val status = try { connection.responseCode } catch (failure: IOException) {
            connection.disconnect()
            throw failure
        }
        if (status == HttpURLConnection.HTTP_OK) return connection
        if (status in listOf(301, 302, 303, 307, 308)) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location == null) throw IOException("Missing update redirect address")
            url = URL(url, location)
        } else {
            connection.disconnect()
            if (status == 403 || status == 429) throw UpdateRateLimitException()
            throw IOException("Update server returned HTTP $status")
        }
    }
    throw IOException("Too many update download redirects")
}
