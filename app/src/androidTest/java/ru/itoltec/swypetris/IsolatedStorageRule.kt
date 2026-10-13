package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/** Подменяет хранилище до создания Activity, чтобы тесты не изменяли данные пользователя. */
class IsolatedStorageRule(private val migrated: Boolean = true) : ExternalResource() {
    /** Создаёт отдельное пустое хранилище для каждой проверки. */
    override fun before() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        GameStorage.testPreferences = application.getSharedPreferences("test_${java.util.UUID.randomUUID()}", 0)
        if (migrated) GameStorage.migrate(requireNotNull(GameStorage.testPreferences))
    }

    /** Очищает только тестовые значения и снимает подмену после закрытия Activity. */
    override fun after() {
        GameStorage.testPreferences?.edit()?.clear()?.commit()
        GameStorage.testPreferences = null
    }
}
