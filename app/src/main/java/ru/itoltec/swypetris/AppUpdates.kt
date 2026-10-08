package ru.itoltec.swypetris

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability

internal const val GITHUB_RELEASE_API = "https://api.github.com/repos/RoyMatus/Swypetris/releases/latest"
private const val RUSTORE_INSTALLER = "ru.vk.store"
private const val REMIND_INTERVAL_MS = 24L * 60 * 60 * 1000

internal data class AvailableUpdate(val versionCode: Long, val versionName: String,
    val apkUrl: String?, val fromRuStore: Boolean,
    val sha256: String? = null, val sizeBytes: Long = 0)

internal sealed interface UpdateNotice {
    data class Available(val update: AvailableUpdate) : UpdateNotice
    data class StoreReady(val update: AvailableUpdate) : UpdateNotice
    data object StoreInstalling : UpdateNotice
    data object Current : UpdateNotice
    data object Failed : UpdateNotice
    data class RateLimited(val retryAtMillis: Long) : UpdateNotice
}

internal fun newerVersion(available: Long, installed: Long): Boolean = available > installed

/** A GitHub Release must carry metadata generated from its signed APK. */
internal fun parseGitHubRelease(release: JSONObject, metadata: JSONObject): AvailableUpdate? {
    val versionName = metadata.optString("versionName")
    val versionCode = metadata.optLong("versionCode", 0)
    val sha256 = metadata.optString("sha256")
    val validVersion = versionCode > 0 && Regex("\\d+\\.\\d+\\.\\d+").matches(versionName) &&
        release.optString("tag_name") == "v$versionName"
    val assets = release.optJSONArray("assets")
    return if (validVersion && Regex("[a-fA-F0-9]{64}").matches(sha256) && assets != null)
        parseReleaseAssets(assets, versionCode, versionName, sha256) else null
}

private fun parseReleaseAssets(assets: org.json.JSONArray, versionCode: Long, versionName: String,
    sha256: String): AvailableUpdate? {
    val expected = "https://github.com/RoyMatus/Swypetris/releases/download/v$versionName/Swypetris.apk"
    var apkUrl: String? = null
    var sizeBytes = 0L
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index)
        if (isUploadedAsset(asset, "Swypetris.apk") && asset?.optString("browser_download_url") == expected) {
            sizeBytes = releaseApkSize(requireNotNull(asset), sha256) ?: return null
            apkUrl = expected
        }
    }
    return apkUrl?.let { AvailableUpdate(versionCode, versionName, it, false, sha256, sizeBytes) }
}

private fun isUploadedAsset(asset: JSONObject?, name: String): Boolean =
    asset != null && asset.optString("name") == name && asset.optString("state") == "uploaded"

private fun releaseApkSize(asset: JSONObject, sha256: String): Long? {
    val digest = if (asset.isNull("digest")) "" else asset.optString("digest").removePrefix("sha256:")
    val validDigest = digest.isEmpty() || digest.equals(sha256, true)
    val size = asset.optLong("size", 0)
    return if (validDigest && size in 1..MAX_UPDATE_BYTES) size else null
}

internal fun isRuStoreInstall(context: Context): Boolean {
    val manager = context.packageManager
    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        manager.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        manager.getInstallerPackageName(context.packageName)
    }
    return installer == RUSTORE_INSTALLER
}

/** Owns update checks for the Activity; gameplay state remains in GameViewModel. */
internal class AppUpdates(private val activity: ComponentActivity,
    private val preferences: android.content.SharedPreferences = activity.getSharedPreferences("app_updates",
        Context.MODE_PRIVATE),
    private val clock: () -> Long = System::currentTimeMillis,
    private val githubUpdate: () -> AvailableUpdate? = ::fetchGitHubUpdate) {
    val delivery = UpdateDelivery(activity)
    private val githubChecks = GitHubChecks()
    private val storeChecks = StoreChecks()
    private val storeManager by lazy { RuStoreAppUpdateManagerFactory.create(activity) }
    private var checking = false
    private var manualRequested = false
    private var storeListener: InstallStateUpdateListener? = null
    var automaticEnabled by mutableStateOf(preferences.getBoolean("automatic", false))
        private set
    var consentRequested by mutableStateOf(false)
        private set
    var notice by mutableStateOf<UpdateNotice?>(null)
        private set

    fun check(manual: Boolean) {
        if (manual && delivery.showReady()) return
        if (checking) {
            if (manual) manualRequested = true
        } else {
        val now = clock()
        val fromStore = isRuStoreInstall(activity)
        if (!fromStore && !githubChecks.allowed(manual, now)) return
        checking = true
        manualRequested = manual
        if (fromStore) storeChecks.check(now)
        else githubChecks.check(now)
        }
    }

    private inner class GitHubChecks {
        fun allowed(manual: Boolean, now: Long): Boolean {
            val retryAt = preferences.getLong("retry_at", Long.MIN_VALUE)
            if (now < retryAt) {
                if (manual) notice = UpdateNotice.RateLimited(retryAt)
                return false
            }
            return if (manual) true else allowAutomaticCheck(now)
        }

        private fun allowAutomaticCheck(now: Long): Boolean {
            val last = if (preferences.contains("last_check")) preferences.getLong("last_check", 0) else null
            val rebased = last?.coerceAtMost(now)
            val rebasedSaved = last == rebased || preferences.edit().putLong("last_check", rebased!!).commit()
            if (!rebasedSaved || !automaticCheckDue(now, rebased)) return false
            // Persist before requesting so Activity/process death or an HTTP failure cannot cause a launch storm.
            return preferences.edit().putLong("last_check", now).commit()
        }

        fun check(now: Long) {
            activity.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { githubUpdate() } }
                val update = result.getOrNull()
                checking = false
                val manual = manualRequested
                manualRequested = false
                if (update == null) {
                    val failure = result.exceptionOrNull() as? UpdateRateLimitException
                    val persisted = failure == null || preferences.edit().putLong("retry_at",
                        failure.retryAtMillis).commit()
                    if (manual) notice = if (persisted &&
                        failure != null) UpdateNotice.RateLimited(failure.retryAtMillis)
                        else UpdateNotice.Failed
                    return@launch
                }
                preferences.edit().putLong("last_check", now).remove("retry_at").apply()
                if (newerVersion(update.versionCode, installedVersionCode())) showAvailable(update, manual)
                else if (manual) notice = UpdateNotice.Current
            }
        }
    }

    private inner class StoreChecks {
        fun check(now: Long) {
            storeManager.getAppUpdateInfo().addOnSuccessListener { info ->
                activity.runOnUiThread {
                    checking = false
                    val manual = manualRequested
                    manualRequested = false
                    preferences.edit().putLong("last_check", now).apply()
                    val currentCode = installedVersionCode()
                    if ((info.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ||
                        info.updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) &&
                        newerVersion(info.availableVersionCode, currentCode)) {
                        val update = AvailableUpdate(info.availableVersionCode, info.availableVersionName, null, true)
                        showAvailable(update, manual)
                    } else if (manual) notice = UpdateNotice.Current
                }
            }.addOnFailureListener {
                activity.runOnUiThread {
                    checking = false
                    if (manualRequested) notice = UpdateNotice.Failed
                    manualRequested = false
                }
            }
        }

        fun clearListener() {
            storeListener?.let(storeManager::unregisterListener)
            storeListener = null
        }
    }

    internal fun showAvailable(update: AvailableUpdate, manual: Boolean) {
        val dismissedCode = preferences.getLong("dismissed_code", 0)
        val dismissedAt = preferences.getLong("dismissed_at", 0)
        if (manual || update.versionCode != dismissedCode ||
            System.currentTimeMillis() - dismissedAt >= REMIND_INTERVAL_MS) {
            notice = UpdateNotice.Available(update)
        }
    }

    fun requestAutomatic(enabled: Boolean) {
        if (enabled) consentRequested = true
        else {
            automaticEnabled = false
            preferences.edit().putBoolean("automatic", false).putBoolean("automatic_choice", true).apply()
        }
    }

    fun answerConsent(enabled: Boolean) {
        automaticEnabled = enabled
        consentRequested = false
        preferences.edit().putBoolean("automatic", enabled).putBoolean("automatic_choice", true).apply()
        if (enabled) {
            (notice as? UpdateNotice.Available)?.update?.let { open(it, automatic = true) }
                ?: check(manual = false)
        }
    }

    fun postponeConsent() {
        consentRequested = false
        dismiss()
    }

    fun installAutomaticallyIfReady(model: GameViewModel) {
        if (!automaticEnabled || model.screen != GameScreen.MENU) return
        delivery.installAutomatically(model)
        val storeUpdate = (notice as? UpdateNotice.StoreReady)?.update
        if (storeUpdate != null && preferences.getLong("automatic_store_attempt", 0) != storeUpdate.versionCode) {
            preferences.edit().putLong("automatic_store_attempt", storeUpdate.versionCode).apply()
            installStore(model, automatic = true)
        }
    }

    fun dismiss() {
        val update = when (val current = notice) {
            is UpdateNotice.Available -> current.update
            is UpdateNotice.StoreReady -> current.update
            else -> null
        }
        update?.let {
            preferences.edit().putLong("dismissed_code", it.versionCode)
                .putLong("dismissed_at", System.currentTimeMillis()).apply()
        }
        notice = null
    }

    fun open(update: AvailableUpdate, automatic: Boolean = false) {
        notice = null
        if (!update.fromRuStore) {
            delivery.download(update, automatic)
            return
        }
        // A fresh AppUpdateInfo is required after the availability check.
        storeManager.getAppUpdateInfo().addOnSuccessListener { info ->
            if (info.updateAvailability != UpdateAvailability.UPDATE_AVAILABLE &&
                info.updateAvailability != UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                activity.runOnUiThread { notice = UpdateNotice.Current }
                return@addOnSuccessListener
            }
            if (info.installStatus == InstallStatus.DOWNLOADED) {
                activity.runOnUiThread { notice = UpdateNotice.StoreReady(update) }
                return@addOnSuccessListener
            }
            val options = AppUpdateOptions.Builder().appUpdateType(
                if (automatic) AppUpdateType.SILENT else AppUpdateType.FLEXIBLE).build()
            val listener = InstallStateUpdateListener { state -> activity.runOnUiThread {
                if (state.installStatus == InstallStatus.DOWNLOADED) {
                    notice = UpdateNotice.StoreReady(update)
                    storeChecks.clearListener()
                } else if (state.installStatus == InstallStatus.FAILED ||
                    state.installStatus == InstallStatus.DOWNLOAD_INTERRUPTED) {
                    storeChecks.clearListener()
                    notice = UpdateNotice.Failed
                }
            } }
            storeChecks.clearListener()
            storeListener = listener
            storeManager.registerListener(listener)
            if (info.updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                return@addOnSuccessListener
            storeManager.startUpdateFlow(info, options).addOnFailureListener {
                activity.runOnUiThread { storeChecks.clearListener(); notice = UpdateNotice.Failed }
            }
        }.addOnFailureListener { activity.runOnUiThread { notice = UpdateNotice.Failed } }
    }

    fun installStore(model: GameViewModel, automatic: Boolean = false) {
        model.pause()
        notice = UpdateNotice.StoreInstalling
        activity.lifecycleScope.launch {
            val persisted = withContext(Dispatchers.IO) {
                val app = activity.application
                preferences.edit().commit() && GameStorage.preferences(app).edit().commit() &&
                    GameStorage.sessionPreferences(app).edit().commit()
            }
            if (!persisted) { notice = UpdateNotice.Failed; return@launch }
            val options = AppUpdateOptions.Builder().appUpdateType(
                if (automatic) AppUpdateType.SILENT else AppUpdateType.FLEXIBLE).build()
            storeManager.completeUpdate(options).addOnFailureListener {
                activity.runOnUiThread { notice = UpdateNotice.Failed }
            }
        }
    }

    fun close() {
        storeChecks.clearListener()
        delivery.close()
    }

    private fun installedVersionCode(): Long {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }
}

private fun fetchGitHubUpdate(): AvailableUpdate? {
    val release = readJson(GITHUB_RELEASE_API)
    val assets = release.optJSONArray("assets") ?: return null
    val metadataUrl = (0 until assets.length()).asSequence().mapNotNull { assets.optJSONObject(it) }
        .firstOrNull { isUploadedAsset(it, "update.json") }?.optString("browser_download_url")
    val version = release.optString("tag_name")
    val expected = "https://github.com/RoyMatus/Swypetris/releases/download/$version/update.json"
    return if (metadataUrl == expected) parseGitHubRelease(release, readJson(expected)) else null
}

private fun readJson(url: String): JSONObject {
    val connection = openUpdateConnection(url, "application/vnd.github+json")
    try {
        val data = connection.inputStream.use { it.readBytesLimited(256 * 1024) }
        return JSONObject(data.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
    } finally {
        connection.disconnect()
    }
}
