package com.listenquran.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import com.listenquran.BuildConfig
import com.listenquran.data.remote.GithubUpdateService
import com.listenquran.data.remote.dto.GithubReleaseDto
import com.listenquran.data.remote.dto.UpdateManifestDto
import com.listenquran.player.AudioPlayerManager
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Immutable
data class UpdateReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256Digest: String?,
)

@Immutable
sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val checkedAtMs: Long) : AppUpdateState
    data class UpdateAvailable(val release: UpdateReleaseInfo) : AppUpdateState
    data class Downloading(
        val release: UpdateReleaseInfo,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : AppUpdateState {
        val progress: Float
            get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    data class Verifying(val release: UpdateReleaseInfo) : AppUpdateState
    data class NeedsInstallPermission(val release: UpdateReleaseInfo, val apkFile: File) : AppUpdateState
    data class ReadyToInstall(val release: UpdateReleaseInfo, val apkFile: File) : AppUpdateState
    data class SignatureConflict(val release: UpdateReleaseInfo) : AppUpdateState
    data class Error(val message: String, val release: UpdateReleaseInfo? = null) : AppUpdateState
}

internal object GithubReleaseMapper {
    fun mapIfNewer(dto: GithubReleaseDto, currentVersionName: String): UpdateReleaseInfo? {
        if (dto.draft || dto.prerelease) return null
        val remoteSemVer = SemVer.parse(dto.tagName) ?: return null
        val currentSemVer = SemVer.parse(currentVersionName) ?: return null
        if (remoteSemVer <= currentSemVer) return null

        val apkAsset = dto.assets.firstOrNull { asset ->
            asset.name.startsWith("ListenQuran-", ignoreCase = true) &&
                asset.name.endsWith(".apk", ignoreCase = true)
        } ?: dto.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
        } ?: return null

        if (apkAsset.browserDownloadUrl.isBlank()) return null

        return UpdateReleaseInfo(
            tagName = dto.tagName,
            versionName = remoteSemVer.toString(),
            title = dto.name?.takeIf { it.isNotBlank() } ?: "Listen Quran ${dto.tagName}",
            changelog = dto.body?.trim().orEmpty(),
            htmlUrl = dto.htmlUrl.ifBlank {
                "https://github.com/tokitauhid/listen-quran/releases/tag/${dto.tagName}"
            },
            publishedAt = dto.publishedAt,
            assetName = apkAsset.name,
            downloadUrl = apkAsset.browserDownloadUrl,
            sizeBytes = apkAsset.size,
            sha256Digest = apkAsset.digest,
        )
    }

    fun mapManifestIfNewer(
        dto: UpdateManifestDto,
        currentVersionName: String,
        currentVersionCode: Int,
    ): UpdateReleaseInfo? {
        if (dto.assetName.isBlank() || dto.downloadUrl.isBlank()) return null
        val remoteSemVer = SemVer.parse(dto.versionName.ifBlank { dto.tagName }) ?: return null
        val currentSemVer = SemVer.parse(currentVersionName) ?: return null
        val isNewer = remoteSemVer > currentSemVer ||
            (remoteSemVer == currentSemVer && dto.versionCode > currentVersionCode)
        if (!isNewer) return null

        return UpdateReleaseInfo(
            tagName = dto.tagName,
            versionName = remoteSemVer.toString(),
            title = dto.title?.takeIf { it.isNotBlank() } ?: "Listen Quran ${dto.tagName}",
            changelog = dto.changelog?.trim().orEmpty(),
            htmlUrl = dto.htmlUrl.ifBlank {
                "https://github.com/tokitauhid/listen-quran/releases/tag/${dto.tagName}"
            },
            publishedAt = dto.publishedAt,
            assetName = dto.assetName,
            downloadUrl = dto.downloadUrl,
            sizeBytes = dto.sizeBytes,
            sha256Digest = dto.sha256,
        )
    }

    fun shouldAutoCheck(
        autoCheckEnabled: Boolean,
        lastCheckedAtMs: Long,
        nowMs: Long,
        intervalMs: Long = AUTO_CHECK_INTERVAL_MS,
    ): Boolean {
        if (!autoCheckEnabled) return false
        if (lastCheckedAtMs <= 0L) return true
        return (nowMs - lastCheckedAtMs) >= intervalMs
    }

    const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12 hours
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val githubUpdateService: GithubUpdateService,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val prefsStore: UpdatePreferencesStore,
    private val audioPlayerManagerProvider: Provider<AudioPlayerManager>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val releaseAdapter by lazy { moshi.adapter(GithubReleaseDto::class.java) }
    private val manifestAdapter by lazy { moshi.adapter(UpdateManifestDto::class.java) }
    private val downloadOkHttp by lazy {
        okHttpClient.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val updatesDir: File
        get() = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private val _showUpdateSheet = MutableStateFlow(false)
    val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

    val autoCheckEnabled: StateFlow<Boolean> = prefsStore.snapshotFlow
        .map { it.autoCheckEnabled }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val dismissedTag: StateFlow<String?> = prefsStore.snapshotFlow
        .map { it.dismissedTag }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val lastCheckedAtMs: StateFlow<Long> = prefsStore.snapshotFlow
        .map { it.lastCheckedAtMs }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    @Volatile
    private var checkJob: Job? = null

    @Volatile
    private var downloadJob: Job? = null

    @Volatile
    private var activeCall: Call? = null

    init {
        scope.launch {
            cleanupStaleApks()
        }
    }

    fun openUpdateSheet() {
        _showUpdateSheet.value = true
    }

    fun closeUpdateSheet() {
        _showUpdateSheet.value = false
    }

    fun dismissBannerForTag(tagName: String) {
        scope.launch {
            prefsStore.dismissTag(tagName)
        }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        scope.launch {
            prefsStore.setAutoCheckEnabled(enabled)
        }
    }

    fun autoCheckIfDue() {
        scope.launch {
            val snap = prefsStore.getSnapshot()
            if (GithubReleaseMapper.shouldAutoCheck(snap.autoCheckEnabled, snap.lastCheckedAtMs, System.currentTimeMillis())) {
                checkForUpdatesInternal(manual = false)
            }
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            checkForUpdatesInternal(manual = manual)
        }
    }

    private suspend fun checkForUpdatesInternal(manual: Boolean) {
        val currentState = _updateState.value
        if (currentState is AppUpdateState.Downloading || currentState is AppUpdateState.Verifying) {
            return
        }
        if (manual) {
            _updateState.value = AppUpdateState.Checking
        }

        val snap = prefsStore.getSnapshot()
        val now = System.currentTimeMillis()

        // Primary: zero-rate-limit CDN-hosted update.json on GitHub Releases
        val manifestReq = Request.Builder()
            .url(CDN_MANIFEST_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "ListenQuran-Android")
            .apply {
                if (!snap.eTag.isNullOrBlank()) header("If-None-Match", snap.eTag)
            }
            .build()

        val manifestAttempt = runCatching {
            okHttpClient.newCall(manifestReq).execute().use { resp ->
                when (resp.code) {
                    304 -> ManifestCheckOutcome.NotModified
                    404 -> ManifestCheckOutcome.NotFound
                    in 200..299 -> {
                        val rawJson = resp.body?.string().orEmpty()
                        val dto = runCatching { manifestAdapter.fromJson(rawJson) }.getOrNull()
                        if (dto == null) {
                            ManifestCheckOutcome.Failed("Invalid update manifest received.")
                        } else {
                            ManifestCheckOutcome.Success(
                                dto = dto,
                                rawJson = rawJson,
                                eTag = resp.header("ETag") ?: resp.header("etag"),
                            )
                        }
                    }
                    else -> ManifestCheckOutcome.Failed("Update check failed (HTTP ${resp.code}).")
                }
            }
        }

        val outcome = manifestAttempt.getOrElse { err ->
            if (manual) {
                _updateState.value = AppUpdateState.Error(
                    message = err.message?.takeIf { it.isNotBlank() } ?: "Could not reach GitHub. Check your internet connection."
                )
            }
            return
        }

        when (outcome) {
            is ManifestCheckOutcome.Success -> {
                prefsStore.saveCheckResult(eTag = outcome.eTag, releaseJson = outcome.rawJson, checkedAtMs = now)
                val mapped = GithubReleaseMapper.mapManifestIfNewer(
                    outcome.dto,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                )
                applyUpdateInfo(mapped, now, manual)
                return
            }
            ManifestCheckOutcome.NotModified -> {
                prefsStore.updateCheckedTimestamp(now)
                val cachedManifest = snap.cachedReleaseJson?.let { raw ->
                    runCatching { manifestAdapter.fromJson(raw) }.getOrNull()
                }
                if (cachedManifest != null) {
                    val mapped = GithubReleaseMapper.mapManifestIfNewer(
                        cachedManifest,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    )
                    applyUpdateInfo(mapped, now, manual)
                    return
                }
                // Fallback if cached JSON wasn't a manifest yet
            }
            is ManifestCheckOutcome.Failed -> {
                if (manual) {
                    _updateState.value = AppUpdateState.Error(outcome.message)
                }
                return
            }
            ManifestCheckOutcome.NotFound -> {
                // Fall back to GitHub Releases API below for releases predating update.json
            }
        }

        // Secondary fallback: GitHub Releases API (if update.json is missing)
        val response = try {
            githubUpdateService.getLatestRelease(ifNoneMatch = snap.eTag)
        } catch (e: Exception) {
            if (manual) {
                _updateState.value = AppUpdateState.Error(
                    message = e.message?.takeIf { it.isNotBlank() } ?: "Could not reach GitHub. Check your internet connection."
                )
            }
            return
        }

        if (response.code() == 304) {
            prefsStore.updateCheckedTimestamp(now)
            val cachedDto = snap.cachedReleaseJson?.let { raw -> runCatching { releaseAdapter.fromJson(raw) }.getOrNull() }
            val mapped = cachedDto?.let { GithubReleaseMapper.mapIfNewer(it, BuildConfig.VERSION_NAME) }
            applyUpdateInfo(mapped, now, manual)
            return
        }

        if (!response.isSuccessful) {
            if (manual) {
                val msg = if (response.code() == 403) {
                    "GitHub API rate limit reached. Try again later."
                } else {
                    "Update check failed (HTTP ${response.code()})."
                }
                _updateState.value = AppUpdateState.Error(msg)
            }
            return
        }

        val dto = response.body()
        val newETag = response.headers()["ETag"] ?: response.headers()["etag"]
        val dtoJson = dto?.let { runCatching { releaseAdapter.toJson(it) }.getOrNull() }
        prefsStore.saveCheckResult(eTag = newETag, releaseJson = dtoJson, checkedAtMs = now)
        val mapped = dto?.let { GithubReleaseMapper.mapIfNewer(it, BuildConfig.VERSION_NAME) }
        applyUpdateInfo(mapped, now, manual)
    }

    private sealed interface ManifestCheckOutcome {
        data class Success(val dto: UpdateManifestDto, val rawJson: String, val eTag: String?) : ManifestCheckOutcome
        data object NotModified : ManifestCheckOutcome
        data object NotFound : ManifestCheckOutcome
        data class Failed(val message: String) : ManifestCheckOutcome
    }

    private suspend fun applyUpdateInfo(update: UpdateReleaseInfo?, checkedAtMs: Long, manual: Boolean) {
        if (update == null) {
            _updateState.value = AppUpdateState.UpToDate(checkedAtMs)
            return
        }

        val cachedApk = File(updatesDir, "ListenQuran-${update.tagName}.apk")
        if (cachedApk.exists() && isValidDownloadedApk(cachedApk, update)) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            _updateState.value = if (canInstall) {
                AppUpdateState.ReadyToInstall(update, cachedApk)
            } else {
                AppUpdateState.NeedsInstallPermission(update, cachedApk)
            }
            return
        }

        if (manual || _updateState.value !is AppUpdateState.UpdateAvailable) {
            _updateState.value = AppUpdateState.UpdateAvailable(update)
        }
    }

    fun downloadUpdate(release: UpdateReleaseInfo) {
        if (downloadJob?.isActive == true) return
        if (!ApkVerifier.isAllowedDownloadUrl(release.downloadUrl)) {
            _updateState.value = AppUpdateState.Error("Blocked untrusted download host.", release)
            return
        }

        downloadJob = scope.launch {
            val partFile = File(updatesDir, "ListenQuran-${release.tagName}.apk.part")
            val finalFile = File(updatesDir, "ListenQuran-${release.tagName}.apk")

            try {
                if (finalFile.exists() && isValidDownloadedApk(finalFile, release)) {
                    transitionToInstallState(release, finalFile)
                    return@launch
                }
                partFile.delete()

                _updateState.value = AppUpdateState.Downloading(release, 0L, release.sizeBytes)

                val request = Request.Builder()
                    .url(release.downloadUrl)
                    .header("User-Agent", "ListenQuran-Android")
                    .build()
                val call = downloadOkHttp.newCall(request)
                activeCall = call

                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Download failed (HTTP ${resp.code})")
                    }
                    val body = resp.body ?: throw IllegalStateException("Empty download response")
                    val total = body.contentLength().takeIf { it > 0L } ?: release.sizeBytes
                    var downloaded = 0L
                    var lastEmitMs = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastEmitMs >= 100L || downloaded == total) {
                                    lastEmitMs = now
                                    _updateState.value = AppUpdateState.Downloading(release, downloaded, total)
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                _updateState.value = AppUpdateState.Verifying(release)

                if (!release.sha256Digest.isNullOrBlank() && !ApkVerifier.verifySha256(finalFile, release.sha256Digest)) {
                    finalFile.delete()
                    _updateState.value = AppUpdateState.Error("Checksum verification failed. Please retry.", release)
                    return@launch
                }

                when (ApkVerifier.verifyApkArchive(context, finalFile, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong())) {
                    ArchiveVerifyResult.Valid -> {
                        transitionToInstallState(release, finalFile)
                    }
                    ArchiveVerifyResult.SignatureMismatch -> {
                        finalFile.delete()
                        _updateState.value = AppUpdateState.SignatureConflict(release)
                    }
                    ArchiveVerifyResult.WrongPackage,
                    ArchiveVerifyResult.VersionNotNewer,
                    ArchiveVerifyResult.CorruptArchive -> {
                        finalFile.delete()
                        _updateState.value = AppUpdateState.Error("Downloaded APK failed verification.", release)
                    }
                }
            } catch (_: CancellationException) {
                partFile.delete()
                _updateState.value = AppUpdateState.UpdateAvailable(release)
            } catch (e: Exception) {
                partFile.delete()
                val msg = if (activeCall?.isCanceled() == true) {
                    _updateState.value = AppUpdateState.UpdateAvailable(release)
                    return@launch
                } else {
                    e.message?.takeIf { it.isNotBlank() } ?: "Download failed."
                }
                _updateState.value = AppUpdateState.Error(msg, release)
            } finally {
                activeCall = null
            }
        }
    }

    fun cancelDownload() {
        activeCall?.cancel()
        downloadJob?.cancel()
        downloadJob = null
    }

    private fun isValidDownloadedApk(file: File, release: UpdateReleaseInfo): Boolean {
        if (!release.sha256Digest.isNullOrBlank() && !ApkVerifier.verifySha256(file, release.sha256Digest)) {
            file.delete()
            return false
        }
        val res = ApkVerifier.verifyApkArchive(context, file, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong())
        if (res != ArchiveVerifyResult.Valid) {
            if (res == ArchiveVerifyResult.CorruptArchive || res == ArchiveVerifyResult.WrongPackage) {
                file.delete()
            }
            return false
        }
        return true
    }

    private fun transitionToInstallState(release: UpdateReleaseInfo, apkFile: File) {
        val canInstall = context.packageManager.canRequestPackageInstalls()
        _updateState.value = if (canInstall) {
            AppUpdateState.ReadyToInstall(release, apkFile)
        } else {
            AppUpdateState.NeedsInstallPermission(release, apkFile)
        }
    }

    fun buildUnknownSourcesIntent(hostContext: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${hostContext.packageName}")
        )
    }

    fun onResumeCheckInstallPermission(hostContext: Context) {
        val state = _updateState.value
        if (state is AppUpdateState.NeedsInstallPermission && hostContext.packageManager.canRequestPackageInstalls()) {
            _updateState.value = AppUpdateState.ReadyToInstall(state.release, state.apkFile)
            launchInstall(hostContext)
        }
    }

    fun launchInstall(hostContext: Context) {
        val state = _updateState.value
        val apkFile = when (state) {
            is AppUpdateState.ReadyToInstall -> state.apkFile
            is AppUpdateState.NeedsInstallPermission -> {
                if (!hostContext.packageManager.canRequestPackageInstalls()) return
                state.apkFile
            }
            else -> return
        }
        if (!apkFile.exists()) {
            val rel = (state as? AppUpdateState.ReadyToInstall)?.release ?: (state as? AppUpdateState.NeedsInstallPermission)?.release
            _updateState.value = AppUpdateState.Error("Update file expired. Please download again.", rel)
            return
        }

        // Preserve queue, active surah, and position right before package replacement.
        scope.launch {
            withContext(Dispatchers.Main) {
                runCatching {
                    val pm = audioPlayerManagerProvider.get()
                    pm.saveSessionSync(500L)
                    if (pm.playbackState.value.isPlaying) {
                        pm.togglePlayPause()
                    }
                }
            }
            val uri = FileProvider.getUriForFile(
                hostContext,
                "${hostContext.packageName}.fileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                hostContext.startActivity(installIntent)
            }.onFailure { err ->
                val rel = (state as? AppUpdateState.ReadyToInstall)?.release ?: (state as? AppUpdateState.NeedsInstallPermission)?.release
                _updateState.value = AppUpdateState.Error(err.message ?: "Could not open package installer.", rel)
            }
        }
    }

    private fun cleanupStaleApks() {
        val current = SemVer.parse(BuildConfig.VERSION_NAME) ?: return
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part", ignoreCase = true)) {
                file.delete()
                return@forEach
            }
            if (file.name.endsWith(".apk", ignoreCase = true)) {
                val verToken = file.name.removePrefix("ListenQuran-").removeSuffix(".apk")
                val parsed = SemVer.parse(verToken)
                if (parsed == null || parsed <= current) {
                    file.delete()
                }
            }
        }
    }

    companion object {
        const val CDN_MANIFEST_URL = "https://github.com/tokitauhid/listen-quran/releases/latest/download/update.json"
    }
}
