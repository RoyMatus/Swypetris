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
import java.net.HttpURLConnection
import java.net.URL

internal const val GITHUB_RELEASE_API = "https://api.github.com/repos/RoyMatus/Swypetris/releases/latest"
private const val RUSTORE_INSTALLER = "ru.vk.store"
private const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
private const val REMIND_INTERVAL_MS = 24L * 60 * 60 * 1000

internal data class AvailableUpdate(val versionCode: Long, val versionName: String,
    val apkUrl: String?, val fromRuStore: Boolean)

internal sealed interface UpdateNotice {
    data class Available(val update: AvailableUpdate) : UpdateNotice
    data object Current : UpdateNotice
    data object Failed : UpdateNotice
}

internal fun newerVersion(available: Long, installed: Long): Boolean = available > installed

/** A GitHub Release must carry metadata generated from its signed APK. */
internal fun parseGitHubRelease(release: JSONObject, metadata: JSONObject): AvailableUpdate? {
    val versionName = metadata.optString("versionName")
    val versionCode = metadata.optLong("versionCode", 0)
    if (versionCode <= 0 || !Regex("\\d+\\.\\d+\\.\\d+").matches(versionName) ||
        release.optString("tag_name") != "v$versionName") return null
    val assets = release.optJSONArray("assets") ?: return null
    var apkUrl: String? = null
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        if (asset.optString("name") != "Swypetris.apk" || asset.optString("state") != "uploaded") continue
        val url = asset.optString("browser_download_url")
        if (url == "https://github.com/RoyMatus/Swypetris/releases/download/v$versionName/Swypetris.apk") {
            val digest = asset.optString("digest").removePrefix("sha256:")
            if (digest.isNotEmpty() && !digest.equals(metadata.optString("sha256"), true)) return null
            apkUrl = url
        }
    }
    return apkUrl?.let { AvailableUpdate(versionCode, versionName, it, false) }
}

internal fun isRuStoreInstall(context: Context): Boolean {
    val manager = context.packageManager
    val installer = if (Build.VERSION.SDK_INT >= 30) {
        manager.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        manager.getInstallerPackageName(context.packageName)
    }
    return installer == RUSTORE_INSTALLER
}

/** Owns update checks for the Activity; gameplay state remains in GameViewModel. */
internal class AppUpdates(private val activity: ComponentActivity) {
    private val preferences = activity.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val storeManager by lazy { RuStoreAppUpdateManagerFactory.create(activity) }
    private var checking = false
    private var manualRequested = false
    private var storeListener: InstallStateUpdateListener? = null
    var notice by mutableStateOf<UpdateNotice?>(null)
        private set

    fun check(manual: Boolean) {
        if (checking) {
            if (manual) manualRequested = true
            return
        }
        val now = System.currentTimeMillis()
        if (!manual && now - preferences.getLong("last_check", 0) < AUTO_CHECK_INTERVAL_MS) return
        checking = true
        manualRequested = manual
        if (isRuStoreInstall(activity)) checkRuStore(now)
        else checkGitHub(now)
    }

    private fun checkRuStore(now: Long) {
        storeManager.getAppUpdateInfo().addOnSuccessListener { info ->
            activity.runOnUiThread {
                checking = false
                val manual = manualRequested
                manualRequested = false
                preferences.edit().putLong("last_check", now).apply()
                val currentCode = installedVersionCode()
                if (info.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE &&
                    newerVersion(info.availableVersionCode, currentCode)) {
                    showAvailable(AvailableUpdate(info.availableVersionCode,
                        info.availableVersionName, null, true), manual)
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

    private fun checkGitHub(now: Long) {
        activity.lifecycleScope.launch {
            val update = withContext(Dispatchers.IO) { runCatching { fetchGitHubUpdate() }.getOrNull() }
            checking = false
            val manual = manualRequested
            manualRequested = false
            if (update == null) {
                if (manual) notice = UpdateNotice.Failed
                return@launch
            }
            preferences.edit().putLong("last_check", now).apply()
            if (newerVersion(update.versionCode, installedVersionCode())) showAvailable(update, manual)
            else if (manual) notice = UpdateNotice.Current
        }
    }

    private fun showAvailable(update: AvailableUpdate, manual: Boolean) {
        val dismissedCode = preferences.getLong("dismissed_code", 0)
        val dismissedAt = preferences.getLong("dismissed_at", 0)
        if (manual || update.versionCode != dismissedCode ||
            System.currentTimeMillis() - dismissedAt >= REMIND_INTERVAL_MS) {
            notice = UpdateNotice.Available(update)
        }
    }

    fun dismiss() {
        (notice as? UpdateNotice.Available)?.update?.let {
            preferences.edit().putLong("dismissed_code", it.versionCode)
                .putLong("dismissed_at", System.currentTimeMillis()).apply()
        }
        notice = null
    }

    fun open(update: AvailableUpdate) {
        notice = null
        if (!update.fromRuStore) {
            if (update.apkUrl == null || !openAppDownload(activity, update.apkUrl)) notice = UpdateNotice.Failed
            return
        }
        // A fresh AppUpdateInfo is required after the availability check.
        storeManager.getAppUpdateInfo().addOnSuccessListener { info ->
            if (info.updateAvailability != UpdateAvailability.UPDATE_AVAILABLE) {
                activity.runOnUiThread { notice = UpdateNotice.Current }
                return@addOnSuccessListener
            }
            val options = AppUpdateOptions.Builder().appUpdateType(AppUpdateType.FLEXIBLE).build()
            val listener = InstallStateUpdateListener { state -> activity.runOnUiThread {
                if (state.installStatus == InstallStatus.DOWNLOADED) {
                    storeManager.completeUpdate(options)
                    clearStoreListener()
                } else if (state.installStatus == InstallStatus.FAILED) {
                    clearStoreListener()
                    notice = UpdateNotice.Failed
                }
            } }
            clearStoreListener()
            storeListener = listener
            storeManager.registerListener(listener)
            storeManager.startUpdateFlow(info, options).addOnFailureListener {
                activity.runOnUiThread { clearStoreListener(); notice = UpdateNotice.Failed }
            }
        }.addOnFailureListener { activity.runOnUiThread { notice = UpdateNotice.Failed } }
    }

    fun close() = clearStoreListener()

    private fun clearStoreListener() {
        storeListener?.let(storeManager::unregisterListener)
        storeListener = null
    }

    private fun installedVersionCode(): Long {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
}

private fun fetchGitHubUpdate(): AvailableUpdate? {
    val release = readJson(GITHUB_RELEASE_API)
    val assets = release.optJSONArray("assets") ?: return null
    var metadataUrl: String? = null
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        if (asset.optString("name") == "update.json" && asset.optString("state") == "uploaded") {
            metadataUrl = asset.optString("browser_download_url")
            break
        }
    }
    val version = release.optString("tag_name")
    val expected = "https://github.com/RoyMatus/Swypetris/releases/download/$version/update.json"
    if (metadataUrl != expected) return null
    return parseGitHubRelease(release, readJson(expected))
}

private fun readJson(url: String): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}
