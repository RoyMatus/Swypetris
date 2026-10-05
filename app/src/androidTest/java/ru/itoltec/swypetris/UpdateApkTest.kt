package ru.itoltec.swypetris

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class UpdateApkTest {
    @Test fun installedApkCannotBeOfferedAsANewerVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apk = File(context.applicationInfo.sourceDir)
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val digest = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        val update = AvailableUpdate(packageVersionCode(installed), installed.versionName.orEmpty(),
            null, false, digest, apk.length())
        assertThrows(IOException::class.java) { validateUpdateApk(context, apk, update) }
        assertThrows(IOException::class.java) { validateUpdateApk(context, apk,
            update.copy(versionCode = update.versionCode + 1)) }
    }

    @Test fun corruptedApkIsRejectedBeforePackageParsing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "corrupt-update-test.apk")
        try {
            file.writeBytes(ByteArray(100) { 42 })
            assertThrows(IOException::class.java) {
                validateUpdateApk(context, file, AvailableUpdate(Long.MAX_VALUE, "9.9.9", null,
                    false, "0".repeat(64), file.length()))
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertThrows(IOException::class.java) {
                validateUpdateApk(context, file, AvailableUpdate(Long.MAX_VALUE, "9.9.9", null,
                    false, hash, file.length()))
            }
        } finally { file.delete() }
    }
}
