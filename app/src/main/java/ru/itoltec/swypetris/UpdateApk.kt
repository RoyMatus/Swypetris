package ru.itoltec.swypetris

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Suppress("DEPRECATION")
internal fun packageVersionCode(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

/** Check the downloaded package before giving it to Android's final signature verifier. */
@Suppress("DEPRECATION")
internal fun validateUpdateApk(context: Context, file: File, update: AvailableUpdate) {
    if (file.length() != update.sizeBytes) throw IOException("Downloaded APK size does not match")
    val hash = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            hash.update(buffer, 0, count)
        }
    }
    if (!hash.digest().joinToString("") { "%02x".format(it) }.equals(update.sha256, true))
        throw IOException("Downloaded APK checksum does not match")
    val manager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
    val installed = manager.getPackageInfo(context.packageName, flags)
    val candidate = manager.getPackageArchiveInfo(file.absolutePath, flags)
        ?: throw IOException("Cannot read APK package or signature")
    if (candidate.packageName != context.packageName || candidate.splitNames?.isNotEmpty() == true ||
        packageVersionCode(candidate) != update.versionCode || candidate.versionName != update.versionName ||
        !newerVersion(update.versionCode, packageVersionCode(installed)))
        throw IOException("APK package or version does not match the expected update")
    val compatible = if (Build.VERSION.SDK_INT >= 28) {
        val current = installed.signingInfo ?: throw IOException("Installed signer is unavailable")
        val next = candidate.signingInfo ?: throw IOException("APK signer is unavailable")
        if (current.hasMultipleSigners() || next.hasMultipleSigners()) {
            current.apkContentsSigners.toSet() == next.apkContentsSigners.toSet()
        } else {
            // Rotation is compatible when the candidate's verified lineage contains the current signer.
            next.signingCertificateHistory?.contains(current.apkContentsSigners.single()) == true
        }
    } else installed.signatures?.toSet() == candidate.signatures?.toSet() &&
        !installed.signatures.isNullOrEmpty()
    if (!compatible) throw IOException("APK is signed by a different publisher")
}
