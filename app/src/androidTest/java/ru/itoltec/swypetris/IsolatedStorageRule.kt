package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/** Подменяет хранилище до создания Activity, чтобы тесты не изменяли данные пользователя. */
class IsolatedStorageRule : ExternalResource() {
    /** Создаёт отдельное пустое хранилище для каждой проверки. */
    override fun before() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        GameStorage.testPreferences = application.getSharedPreferences("test_${java.util.UUID.randomUUID()}", 0)
    }

    /** Очищает только тестовые значения и снимает подмену после закрытия Activity. */
    override fun after() {
        GameStorage.testPreferences?.edit()?.clear()?.commit()
        GameStorage.testPreferences = null
    }
}
