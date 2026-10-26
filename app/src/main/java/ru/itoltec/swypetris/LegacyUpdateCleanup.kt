package ru.itoltec.swypetris

import android.content.Context
import java.io.File

/** Retired APK delivery state must never resurrect an installation prompt after upgrade. */
internal fun removeLegacyUpdate(context: Context) {
    val directory = File(context.noBackupFilesDir, "updates")
    directory.listFiles()?.filter { it.isFile }?.forEach(::removeUpdateFile)
    context.getSharedPreferences("app_update_download", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("app_update_install", Context.MODE_PRIVATE).edit().clear().commit()
}
