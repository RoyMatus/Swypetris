package ru.itoltec.swypetris

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.IOException

internal const val INSTALL_STATE_PREFERENCES = "app_update_install"

/** Android delivers results here even when replacing the application's process. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferences = context.getSharedPreferences(INSTALL_STATE_PREFERENCES, Context.MODE_PRIVATE)
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (id < 0 || id != preferences.getInt("session", -1)) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        // The durable result is also useful when the Activity is absent or the process is replaced.
        preferences.edit().putInt("status", status).commit()
        if (status == PackageInstaller.STATUS_SUCCESS) {
            File(context.noBackupFilesDir, "updates/ready.apk").delete()
            context.getSharedPreferences("app_update_download", Context.MODE_PRIVATE).edit()
                .remove("ready").remove("saved_at").putBoolean("interrupted", false).commit()
        }
        @Suppress("DEPRECATION")
        val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        listener?.invoke(status, confirmation)
    }

    internal companion object {
        var listener: ((Int, Intent?) -> Unit)? = null
    }
}

/** Prepares a fully validated APK; the caller checkpoints gameplay before committing. */
internal class UpdateInstaller(private val context: Context) {
    private val installer = context.packageManager.packageInstaller
    private val preferences = context.getSharedPreferences(INSTALL_STATE_PREFERENCES, Context.MODE_PRIVATE)

    fun prepare(file: File, update: AvailableUpdate, allowWithoutConfirmation: Boolean): Int {
        validateUpdateApk(context, file, update)
        val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        parameters.setAppPackageName(context.packageName)
        parameters.setSize(file.length())
        if (Build.VERSION.SDK_INT >= 31) parameters.setRequireUserAction(
            if (allowWithoutConfirmation) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
            else PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        if (Build.VERSION.SDK_INT >= 33)
            parameters.setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        val id = installer.createSession(parameters)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
            }
            if (!preferences.edit().putInt("session", id).remove("status").commit())
                throw IOException("Cannot persist the installation session")
            return id
        } catch (failure: Exception) {
            installer.abandonSession(id)
            throw failure
        }
    }

    fun commit(id: Int) {
        // API 35+ rejects immutable status receivers. The mutable intent has an explicit private target.
        val callback = Intent(context, UpdateInstallReceiver::class.java)
            .setAction("${context.packageName}.INSTALL_RESULT.$id")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val receiver = PendingIntent.getBroadcast(context, id, callback, flags)
        installer.openSession(id).use { it.commit(receiver.intentSender) }
    }

    fun abandon(id: Int) {
        if (installer.getSessionInfo(id) != null) installer.abandonSession(id)
        if (preferences.getInt("session", -1) == id)
            preferences.edit().remove("session").remove("status").commit()
    }
}
