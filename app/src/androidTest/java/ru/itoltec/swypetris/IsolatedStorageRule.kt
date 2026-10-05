package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Подменяет хранилище до создания Activity, чтобы тесты не изменяли данные пользователя. */
class IsolatedStorageRule : ExternalResource() {
    private var immersiveConfirmation = "null"

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().toString(Charsets.UTF_8).trim()
        }

    /** Создаёт отдельное пустое хранилище для каждой проверки. */
    override fun before() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        GameStorage.testPreferences = application.getSharedPreferences("test_${java.util.UUID.randomUUID()}", 0)
        // Routine UI tests exercise the app after Android's one-time fullscreen explanation.
        immersiveConfirmation = shell("settings get secure immersive_mode_confirmations")
        shell("settings put secure immersive_mode_confirmations confirmed")
    }

    /** Очищает только тестовые значения и снимает подмену после закрытия Activity. */
    override fun after() {
        GameStorage.testPreferences?.edit()?.clear()?.commit()
        GameStorage.testPreferences = null
        if (immersiveConfirmation == "null" || immersiveConfirmation.isEmpty()) {
            shell("settings delete secure immersive_mode_confirmations")
        } else {
            val quoted = immersiveConfirmation.replace("'", "'\\''")
            shell("settings put secure immersive_mode_confirmations '$quoted'")
        }
    }
}
