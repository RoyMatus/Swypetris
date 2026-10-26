package ru.itoltec.swypetris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDeliveryRecoveryTest {
    @Test fun retiredDownloadIsRemovedWithoutChangingGameData() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val directory = File(context.noBackupFilesDir, "updates").apply { mkdirs() }
        val ready = File(directory, "ready.apk").apply { writeText("obsolete APK") }
        val partial = File(directory, "interrupted.part").apply { writeText("partial APK") }
        val download = context.getSharedPreferences("app_update_download", Context.MODE_PRIVATE)
        val install = context.getSharedPreferences("app_update_install", Context.MODE_PRIVATE)
        download.edit().putString("ready", "obsolete metadata").commit()
        install.edit().putInt("session", 42).commit()
        val settings = GameStorage.preferences(context)
        val before = settings.all.toMap()
        removeLegacyUpdate(context)
        removeLegacyUpdate(context)
        assertFalse(ready.exists())
        assertFalse(partial.exists())
        assertTrue(download.all.isEmpty())
        assertTrue(install.all.isEmpty())
        assertEquals(before, settings.all)
    }
}
