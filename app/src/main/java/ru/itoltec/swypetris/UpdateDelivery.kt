package ru.itoltec.swypetris

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

internal sealed interface DeliveryNotice {
    data class Downloading(val received: Long, val total: Long) : DeliveryNotice
    data class Ready(val update: AvailableUpdate) : DeliveryNotice
    data object Permission : DeliveryNotice
    data object Installing : DeliveryNotice
    data object Confirmation : DeliveryNotice
    data class Failed(val message: String) : DeliveryNotice
}

/** Activity-scoped operations stop on destruction; validated APKs and installer results survive it. */
internal class UpdateDelivery(private val activity: ComponentActivity) {
    private val directory = File(activity.noBackupFilesDir, "updates").apply { mkdirs() }
    private val preferences = activity.getSharedPreferences("app_update_download", Context.MODE_PRIVATE)
    private val installPreferences = activity.getSharedPreferences(INSTALL_STATE_PREFERENCES, Context.MODE_PRIVATE)
    private val installer = UpdateInstaller(activity.applicationContext)
    private var operation: Job? = null
    private var ready: AvailableUpdate? = null
    private var attempted: AvailableUpdate? = null
    private var confirmation: Intent? = null
    private var sessionId = -1
    var notice by mutableStateOf<DeliveryNotice?>(null)
        private set
    var showNotice by mutableStateOf(true)
        private set

    init {
        UpdateInstallReceiver.listener = ::installationResult
        restore()
    }

    private fun restore() {
        operation = activity.lifecycleScope.launch {
            var failureMessage: String? = null
            val restored = withContext(Dispatchers.IO) {
                directory.listFiles()?.filter { it.extension == "part" }?.forEach(File::delete)
                val json = preferences.getString("ready", null) ?: run {
                    apk().delete()
                    return@withContext null
                }
                try {
                    val data = JSONObject(json)
                    val update = AvailableUpdate(data.getLong("code"), data.getString("name"),
                        data.getString("url"), false, data.getString("hash"), data.getLong("size"))
                    @Suppress("DEPRECATION")
                    val installed = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    if (!newerVersion(update.versionCode, packageVersionCode(installed))) {
                        forgetReady()
                        return@withContext null
                    }
                    if (System.currentTimeMillis() - preferences.getLong("saved_at", 0) > 7L * 24 * 60 * 60 * 1000)
                        throw IOException("Saved update expired")
                    validateUpdateApk(activity, apk(), update)
                    update
                } catch (_: Exception) {
                    forgetReady()
                    failureMessage = "Сохранённый APK устарел или не прошёл проверку. Проверьте обновления и загрузите файл заново."
                    null
                }
            }
            operation = null
            ready = restored
            sessionId = installPreferences.getInt("session", -1)
            val status = installPreferences.getInt("status", Int.MIN_VALUE)
            val session = if (sessionId >= 0)
                activity.packageManager.packageInstaller.getSessionInfo(sessionId) else null
            val pending = status == Int.MIN_VALUE && session != null &&
                if (Build.VERSION.SDK_INT >= 26) session.isSealed else session.isActive
            if (pending) notice = DeliveryNotice.Installing
            else if (failureMessage != null) notice = DeliveryNotice.Failed(requireNotNull(failureMessage))
            else if (restored != null && System.currentTimeMillis() -
                preferences.getLong("dismissed_at", 0) >= 24L * 60 * 60 * 1000)
                notice = DeliveryNotice.Ready(restored)
            else if (preferences.getBoolean("interrupted", false))
                notice = DeliveryNotice.Failed("Загрузка прервалась. Проверьте обновления и загрузите APK заново.")
            preferences.edit().putBoolean("interrupted", false).apply()
        }
    }

    private fun apk() = File(directory, "ready.apk")

    private fun forgetReady() {
        apk().delete()
        preferences.edit().remove("ready").remove("saved_at").commit()
    }

    fun showReady(): Boolean {
        showNotice = true
        ready?.let {
            preferences.edit().remove("dismissed_at").apply()
            notice = DeliveryNotice.Ready(it)
            return true
        }
        return notice != null
    }

    fun download(update: AvailableUpdate, automatic: Boolean = false) {
        if (operation?.isActive == true || update.fromRuStore) return
        notice = DeliveryNotice.Downloading(0, update.sizeBytes)
        showNotice = !automatic
        attempted = update
        operation = activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (directory.usableSpace < update.sizeBytes * 2)
                        throw IOException("Недостаточно места. Освободите место и повторите загрузку.")
                    val temporary = File(directory, "${UUID.randomUUID()}.part")
                    preferences.edit().putBoolean("interrupted", true).commit()
                    val connection = openUpdateConnection(requireNotNull(update.apkUrl))
                    try {
                        val context = coroutineContext
                        connection.inputStream.use { input ->
                            var lastPercent = -1L
                            copyUpdate(input, temporary, update.sizeBytes, requireNotNull(update.sha256),
                                { context.ensureActive() }, { received ->
                                    val percent = received * 100 / update.sizeBytes
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        activity.runOnUiThread {
                                            if (operation?.isActive == true)
                                                notice = DeliveryNotice.Downloading(received, update.sizeBytes)
                                        }
                                    }
                                })
                        }
                        validateUpdateApk(activity, temporary, update)
                        context.ensureActive()
                        if (!temporary.renameTo(apk())) throw IOException("Cannot save downloaded APK")
                        val metadata = JSONObject().put("code", update.versionCode).put("name", update.versionName)
                            .put("url", update.apkUrl).put("hash", update.sha256).put("size", update.sizeBytes)
                        if (!preferences.edit().putString("ready", metadata.toString())
                                .putLong("saved_at", System.currentTimeMillis()).putBoolean("interrupted", false).commit())
                            throw IOException("Cannot save update metadata")
                    } finally {
                        connection.disconnect()
                        temporary.delete()
                    }
                }
                operation = null
                ready = update
                showNotice = true
                notice = DeliveryNotice.Ready(update)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                showNotice = true
                notice = DeliveryNotice.Failed(if (failure is UpdateRateLimitException)
                    "GitHub временно ограничил загрузки с вашей сети. Попробуйте позже."
                    else "Не удалось загрузить или проверить APK. Проверьте сеть и свободное место, затем повторите загрузку.")
            }
        }
    }

    fun dismiss() {
        if (ready != null) preferences.edit().putLong("dismissed_at", System.currentTimeMillis()).apply()
        notice = null
    }

    fun retry(): Boolean {
        if (showReady()) return true
        val update = attempted ?: return false
        download(update)
        return true
    }

    fun installAutomatically(model: GameViewModel) {
        val update = ready ?: return
        val permissionGranted = Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()
        val permissionResolved = notice == DeliveryNotice.Permission && permissionGranted
        if (operation?.isActive == true || (notice != null && notice !is DeliveryNotice.Ready && !permissionResolved) ||
            preferences.getLong("automatic_attempt", 0) == update.versionCode ||
            System.currentTimeMillis() - preferences.getLong("dismissed_at", 0) < 24L * 60 * 60 * 1000) return
        if (!permissionGranted) {
            showNotice = true
            notice = DeliveryNotice.Permission
            return
        }
        preferences.edit().putLong("automatic_attempt", update.versionCode).apply()
        install(model, allowWithoutConfirmation = true)
    }

    fun cancelDownload() {
        operation?.cancel()
        preferences.edit().putBoolean("interrupted", false).apply()
        notice = null
    }

    fun requestPermission() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= 26)
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            else Intent(Settings.ACTION_SECURITY_SETTINGS)
            activity.startActivity(intent)
            notice = ready?.let(DeliveryNotice::Ready)
        } catch (_: Exception) {
            notice = DeliveryNotice.Failed("Не удалось открыть разрешение установки. Откройте настройки Android и разрешите установку из Swypetris.")
        }
    }

    fun install(model: GameViewModel, allowWithoutConfirmation: Boolean) {
        if (operation?.isActive == true) return
        val update = ready ?: return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            notice = DeliveryNotice.Permission
            return
        }
        model.pause()
        notice = DeliveryNotice.Installing
        operation = activity.lifecycleScope.launch {
            var prepared = -1
            try {
                prepared = withContext(Dispatchers.IO) {
                    // commit waits for the pending settings/session apply writes before process replacement.
                    val app = activity.application as Application
                    if (!preferences.edit().commit() ||
                        !activity.getSharedPreferences("app_updates", Context.MODE_PRIVATE).edit().commit() ||
                        !GameStorage.preferences(app).edit().commit() ||
                        !GameStorage.sessionPreferences(app).edit().commit())
                        throw IOException("Cannot save game before update")
                    if (sessionId >= 0) installer.abandon(sessionId)
                    prepared = installer.prepare(apk(), update, allowWithoutConfirmation)
                    prepared
                }
                sessionId = prepared
                installer.commit(prepared)
            } catch (cancelled: CancellationException) {
                if (prepared >= 0) installer.abandon(prepared)
                throw cancelled
            } catch (_: Exception) {
                if (prepared >= 0) installer.abandon(prepared)
                notice = DeliveryNotice.Failed("Установка не началась. Проверьте разрешение и свободное место; можно повторить установку.")
            }
        }
    }

    private fun installationResult(status: Int, intent: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirmation = intent
                notice = if (intent != null) DeliveryNotice.Confirmation
                    else DeliveryNotice.Failed("Android не предоставил окно подтверждения. Повторите установку.")
            }
            PackageInstaller.STATUS_SUCCESS -> {
                apk().delete()
                preferences.edit().remove("ready").apply()
                ready = null
                notice = null
            }
            else -> {
                val explanation = when (status) {
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Недостаточно места для установки."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android запретил установку. Проверьте разрешения."
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Установка отменена."
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "Версия или подпись APK несовместима с установленным приложением."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Обновление несовместимо с этим устройством."
                    else -> "Android не смог установить обновление."
                }
                notice = DeliveryNotice.Failed("$explanation Можно повторить установку или продолжить игру.")
            }
        }
    }

    fun confirmInstallation() {
        val intent = confirmation ?: return
        try {
            activity.startActivity(intent)
            notice = DeliveryNotice.Installing
        } catch (_: Exception) {
            notice = DeliveryNotice.Failed("Не удалось открыть подтверждение Android. Повторите установку.")
        }
    }

    fun close() {
        operation?.cancel()
        UpdateInstallReceiver.listener = null
    }
}
