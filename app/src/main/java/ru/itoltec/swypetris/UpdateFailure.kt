package ru.itoltec.swypetris

import java.io.IOException
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import java.security.GeneralSecurityException

internal enum class UpdateFailureReason(val userMessage: String) {
    NETWORK("Не удалось загрузить APK с GitHub. Проверьте подключение и повторите загрузку."),
    INCOMPLETE("Загрузка APK прервалась. Повторите загрузку."),
    INTEGRITY("APK не прошёл проверку размера или контрольной суммы. Загрузите обновление заново."),
    PACKAGE("APK не соответствует ожидаемой версии приложения. Проверьте обновления заново."),
    SIGNER("Подпись обновления отличается от подписи установленной сборки. " +
        "Обновить её этим APK нельзя. Используйте сборку из GitHub Releases с " +
        "совместимой подписью; перед переустановкой сохраните нужные данные."),
    STORAGE("Недостаточно места для обновления. Освободите место и повторите загрузку."),
    PERSISTENCE("Не удалось сохранить обновление на устройстве. Проверьте доступное место и повторите загрузку.")
}

internal class UpdateFailure(val reason: UpdateFailureReason, detail: String, cause: Throwable? = null) :
    IOException(detail, cause)

internal enum class UpdateStage { STORAGE, CONNECT, DOWNLOAD, VALIDATE, SAVE }

internal fun updateFailureMessage(failure: Exception, stage: UpdateStage): String = when (failure) {
    is UpdateRateLimitException -> "GitHub временно ограничил загрузки с вашей сети. Попробуйте позже."
    is UpdateFailure -> failure.reason.userMessage
    else -> when (stage) {
        UpdateStage.CONNECT, UpdateStage.DOWNLOAD -> UpdateFailureReason.NETWORK
        UpdateStage.VALIDATE -> UpdateFailureReason.PACKAGE
        UpdateStage.STORAGE, UpdateStage.SAVE -> UpdateFailureReason.PERSISTENCE
    }.userMessage
}

/** Recover documented I/O, metadata and package failures; cancellation keeps its coroutine semantics. */
internal suspend inline fun <T> recoverExpectedUpdateFailure(operation: () -> T, recover: (Exception) -> T): T =
    try { operation() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: IOException) { recover(failure) }
    catch (failure: JSONException) { recover(failure) }
    catch (failure: PackageManager.NameNotFoundException) { recover(failure) }
    catch (failure: SecurityException) { recover(failure) }
    catch (failure: IllegalArgumentException) { recover(failure) }
    catch (failure: IllegalStateException) { recover(failure) }
    catch (failure: GeneralSecurityException) { recover(failure) }
