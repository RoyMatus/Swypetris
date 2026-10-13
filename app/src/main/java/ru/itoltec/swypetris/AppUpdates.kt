package ru.itoltec.swypetris

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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

private enum class CheckDecision { PERMITTED, NOT_DUE, COOLDOWN, MANUAL_LIMIT, PERSISTENCE_FAILED }
private data class CheckPreflight(val decision: CheckDecision, val now: Long, val retryAt: Long = 0)

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
    data class ManualLimited(val retryAfterMillis: Long) : UpdateNotice
    data class StoreBackoff(val retryAfterMillis: Long) : UpdateNotice
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
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val githubUpdate: () -> AvailableUpdate? = ::fetchGitHubUpdate) {
    val delivery = UpdateDelivery(activity)
    private val githubChecks = GitHubChecks()
    private val storeChecks = StoreChecks()
    private val storeManager by lazy { RuStoreAppUpdateManagerFactory.create(activity) }
    private var checking = false
    private var manualRequested = false
    private var storeListener: InstallStateUpdateListener? = null
    var notice by mutableStateOf<UpdateNotice?>(null)
        private set

    init {
        // The removed setting must never retain an old automatic download/install grant.
        if (listOf("automatic", "automatic_choice", "automatic_store_attempt").any(preferences::contains))
            preferences.edit().remove("automatic").remove("automatic_choice")
                .remove("automatic_store_attempt").apply()
    }

    fun check(manual: Boolean) {
        if (manual && delivery.showReady()) return
        if (checking) {
            if (manual) manualRequested = true
            return
        }
        val fromStore = isRuStoreInstall(activity)
        checking = true
        manualRequested = manual
        activity.lifecycleScope.launch {
            var storeStarted = false
            try {
                var preflight = withContext(Dispatchers.IO) { reserveAttempt(manual, fromStore) }
                // A tap joining an automatic preflight only consumes budget if it starts a request.
                if (!manual && preflight.decision == CheckDecision.NOT_DUE && manualRequested)
                    preflight = withContext(Dispatchers.IO) { reserveAttempt(true, fromStore) }
                if (preflight.decision != CheckDecision.PERMITTED) {
                    if (manualRequested) notice = preflightNotice(preflight, fromStore)
                    return@launch
                }
                if (fromStore) {
                    storeChecks.check()
                    storeStarted = true
                } else githubChecks.completeCheck()
            } finally {
                if (!storeStarted) { checking = false; manualRequested = false }
            }
        }
    }

    private fun preflightNotice(preflight: CheckPreflight, fromStore: Boolean): UpdateNotice? {
        val delay = (preflight.retryAt - preflight.now).coerceAtLeast(0)
        return when (preflight.decision) {
            CheckDecision.COOLDOWN -> if (fromStore) UpdateNotice.StoreBackoff(delay)
                else UpdateNotice.RateLimited(saturatingAdd(clock(), delay))
            CheckDecision.MANUAL_LIMIT -> UpdateNotice.ManualLimited(delay)
            CheckDecision.PERSISTENCE_FAILED -> UpdateNotice.Failed
            else -> null
        }
    }

    private fun policyNow(): Triple<Long, Long, Long> {
        val wall = clock()
        val elapsed = elapsedClock()
        val anchorWall = if (preferences.contains("policy_wall")) preferences.getLong("policy_wall", 0) else null
        val anchorElapsed = if (preferences.contains("policy_elapsed"))
            preferences.getLong("policy_elapsed", 0) else null
        return Triple(policyTime(wall, elapsed, anchorWall, anchorElapsed), wall, elapsed)
    }

    @Suppress("ReturnCount") // Each failed gate exits before the durable request reservation.
    private fun reserveAttempt(manual: Boolean, fromStore: Boolean): CheckPreflight {
        val (now, _, elapsed) = policyNow()
        val previousElapsed = preferences.getLong("policy_elapsed", Long.MIN_VALUE)
        if (elapsed < previousElapsed && !preferences.edit().putLong("policy_wall", now)
                .putLong("policy_elapsed", elapsed).commit())
            return CheckPreflight(CheckDecision.PERSISTENCE_FAILED, now)
        val retryAt = preferences.getLong(if (fromStore) "store_retry_at" else "retry_at", Long.MIN_VALUE)
        if (now < retryAt) return CheckPreflight(CheckDecision.COOLDOWN, now, retryAt)
        val editor = preferences.edit().putLong("policy_wall", now).putLong("policy_elapsed", elapsed)
        if (manual) {
            val attempts = parseManualAttempts(preferences.getString("manual_attempts", null))
                ?: return CheckPreflight(CheckDecision.PERSISTENCE_FAILED, now)
            val eligibility = manualEligibility(now, attempts)
            if (eligibility.retryAt != null) return CheckPreflight(CheckDecision.MANUAL_LIMIT, now, eligibility.retryAt)
            editor.putString("manual_attempts", (eligibility.attempts + now).joinToString(","))
        } else {
            val last = if (preferences.contains("last_check")) preferences.getLong("last_check", 0) else null
            if (!automaticCheckDue(now, last)) return CheckPreflight(CheckDecision.NOT_DUE, now)
            editor.putLong("last_check", now)
        }
        // Durable reservation precedes either network/SDK request, including failed attempts.
        return CheckPreflight(if (editor.commit()) CheckDecision.PERMITTED else CheckDecision.PERSISTENCE_FAILED, now)
    }

    private inner class GitHubChecks {
        suspend fun completeCheck() {
            val result = withContext(Dispatchers.IO) {
                runCatching { githubUpdate() }.onFailure { if (it is CancellationException) throw it }
            }
            val update = result.getOrNull()
            if (update == null) {
                val failure = result.exceptionOrNull() as? UpdateRateLimitException
                val persisted = failure == null || withContext(Dispatchers.IO) {
                    val (now, wall, elapsed) = policyNow()
                    val previous = preferences.getInt("secondary_failures", 0)
                    val serverWait = (failure.retryAtMillis - wall).coerceAtLeast(0)
                    val fallback = if (failure.secondaryWithoutDeadline) secondaryBackoffMillis(previous) else 0
                    val deadline = saturatingAdd(now, maxOf(serverWait, fallback))
                    val editor = preferences.edit()
                    editor.putLong("retry_at", maxOf(deadline,
                        preferences.getLong("retry_at", Long.MIN_VALUE)))
                    editor.putInt("secondary_failures", if (failure.secondaryWithoutDeadline)
                        (previous + 1).coerceAtMost(7) else 0)
                    editor.putLong("policy_wall", now).putLong("policy_elapsed", elapsed)
                    editor.commit()
                }
                if (manualRequested) notice = if (persisted && failure != null) {
                    val now = policyNow().first
                    UpdateNotice.RateLimited(saturatingAdd(clock(),
                        (preferences.getLong("retry_at", now) - now).coerceAtLeast(0)))
                }
                    else UpdateNotice.Failed
                return
            }
            preferences.edit().remove("retry_at").remove("secondary_failures").apply()
            if (newerVersion(update.versionCode, installedVersionCode())) showAvailable(update, manualRequested)
            else if (manualRequested) notice = UpdateNotice.Current
        }
    }

    private inner class StoreChecks {
        fun check() {
            storeManager.getAppUpdateInfo().addOnSuccessListener { info ->
                activity.runOnUiThread {
                    activity.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            preferences.edit().remove("store_retry_at").remove("store_failures").commit()
                        }
                        val manual = manualRequested
                        checking = false
                        manualRequested = false
                        val currentCode = installedVersionCode()
                        if ((info.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ||
                            info.updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) &&
                            newerVersion(info.availableVersionCode, currentCode)) {
                            val update = AvailableUpdate(info.availableVersionCode,
                                info.availableVersionName, null, true)
                            showAvailable(update, manual)
                        } else if (manual) notice = UpdateNotice.Current
                    }
                }
            }.addOnFailureListener {
                activity.runOnUiThread {
                    activity.lifecycleScope.launch {
                        val persisted = withContext(Dispatchers.IO) {
                            val (now, _, elapsed) = policyNow()
                            val failures = preferences.getInt("store_failures", 0)
                            val editor = preferences.edit()
                            editor.putLong("store_retry_at", saturatingAdd(now, storeBackoffMillis(failures)))
                            editor.putInt("store_failures", (failures + 1).coerceAtMost(7))
                            editor.putLong("policy_wall", now).putLong("policy_elapsed", elapsed)
                            editor.commit()
                        }
                        if (manualRequested) notice = if (persisted)
                            UpdateNotice.StoreBackoff((preferences.getLong("store_retry_at", 0) -
                                policyNow().first).coerceAtLeast(0))
                            else UpdateNotice.Failed
                        checking = false
                        manualRequested = false
                    }
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

    fun open(update: AvailableUpdate) {
        notice = null
        if (!update.fromRuStore) {
            delivery.download(update)
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
            val options = AppUpdateOptions.Builder().appUpdateType(AppUpdateType.FLEXIBLE).build()
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

    fun installStore(model: GameViewModel) {
        model.pause()
        notice = UpdateNotice.StoreInstalling
        activity.lifecycleScope.launch {
            val persisted = withContext(Dispatchers.IO) {
                val app = activity.application
                preferences.edit().commit() && GameStorage.preferences(app).edit().commit() &&
                    GameStorage.sessionPreferences(app).edit().commit()
            }
            if (!persisted) { notice = UpdateNotice.Failed; return@launch }
            val options = AppUpdateOptions.Builder().appUpdateType(AppUpdateType.FLEXIBLE).build()
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
