package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppReleaseInfo(
    val sourceLabel: String,
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val remoteBuildNumber: Int,
    val isUpdateAvailable: Boolean
)

sealed interface UpdateCheckResult {
    data class Success(val release: AppReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersionLabel: String, val checkedSource: String) : UpdateCheckResult
    data class NotConfigured(val message: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object AppUpdateChecker {
    private const val PREFS_NAME = "kindred_app_updates"
    private const val KEY_GITHUB_REPO = "github_repo_slug"
    private const val KEY_AISTUDIO_URL = "aistudio_app_url"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"
    private const val KEY_LAST_AUTO_CHECK_MS = "last_auto_check_ms"
    private const val AUTO_CHECK_INTERVAL_MS = 5 * 60_000L

    fun currentBuildNumber(): Int {
        return maxOf(BuildConfig.VERSION_CODE, BuildConfig.CI_BUILD_NUMBER)
    }

    fun currentVersionLabel(): String {
        val buildNum = currentBuildNumber()
        return "v${BuildConfig.VERSION_NAME} (Build #$buildNum)"
    }

    fun getConfiguredGitHubRepo(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_GITHUB_REPO, null)?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        return BuildConfig.GITHUB_REPO.trim()
    }

    fun saveGitHubRepo(context: Context, rawInput: String): String {
        val normalized = parseGitHubRepoSlug(rawInput).orEmpty()
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GITHUB_REPO, normalized)
            .apply()
        return normalized
    }

    fun getConfiguredAiStudioUrl(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_AISTUDIO_URL, null)?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        return BuildConfig.AISTUDIO_APP_URL.trim()
    }

    fun saveAiStudioUrl(context: Context, url: String): String {
        val trimmed = url.trim()
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AISTUDIO_URL, trimmed)
            .apply()
        return trimmed
    }

    fun isTagDismissed(context: Context, tagName: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DISMISSED_TAG, null) == tagName
    }

    fun dismissTag(context: Context, tagName: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_TAG, tagName)
            .apply()
    }

    fun parseGitHubRepoSlug(rawInput: String): String? {
        val trimmed = rawInput.trim().removeSuffix("/")
        if (trimmed.isBlank()) return null
        val cleaned = trimmed
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
        val segments = cleaned.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val owner = segments[0]
        val repo = segments[1]
        val validPart = Regex("^[A-Za-z0-9_.-]+$")
        return if (validPart.matches(owner) && validPart.matches(repo)) "$owner/$repo" else null
    }

    fun extractBuildNumber(tagName: String, title: String = ""): Int {
        val buildMatch = Regex("(?:build[-_\\s#]*|#)([0-9]+)", RegexOption.IGNORE_CASE)
            .find("$tagName $title")
        if (buildMatch != null) {
            return buildMatch.groupValues[1].toIntOrNull() ?: 0
        }
        return Regex("([0-9]+)").find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    fun isRemoteReleaseNewer(
        tagName: String,
        title: String = "",
        localBuildNumber: Int = currentBuildNumber(),
        localVersionName: String = BuildConfig.VERSION_NAME
    ): Boolean {
        val remoteBuild = extractBuildNumber(tagName, title)
        if (remoteBuild > 0 && remoteBuild > localBuildNumber) {
            return true
        }
        val cleanRemoteTag = tagName.trim().removePrefix("v").removePrefix("V")
        val cleanLocalVersion = localVersionName.trim().removePrefix("v").removePrefix("V")
        if (cleanRemoteTag.contains('.') && cleanLocalVersion.contains('.')) {
            return compareSemVer(cleanRemoteTag, cleanLocalVersion) > 0
        }
        return false
    }

    private fun compareSemVer(remote: String, local: String): Int {
        val remoteParts = remote.split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val localParts = local.split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    suspend fun checkGitHubLatestRelease(context: Context): UpdateCheckResult {
        val slug = parseGitHubRepoSlug(getConfiguredGitHubRepo(context))
            ?: return UpdateCheckResult.NotConfigured(
                "Enter your GitHub repository (owner/repo) to check for APK releases automatically."
            )
        return checkGitHubLatestReleaseForSlug(slug)
    }

    suspend fun checkGitHubLatestReleaseForSlug(
        repoSlug: String,
        localBuildNumber: Int = currentBuildNumber(),
        localVersionName: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$repoSlug/releases/latest"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Kindred-Android-Updater")
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext UpdateCheckResult.UpToDate(
                    currentVersionLabel = "v$localVersionName (Build #$localBuildNumber)",
                    checkedSource = "GitHub ($repoSlug — no published releases yet)"
                )
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("GitHub check returned HTTP $code")
            }
            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)
            val tagName = json.optString("tag_name", "").trim()
            val title = json.optString("name", tagName).ifBlank { tagName }
            val body = json.optString("body", "New Kindred release published on GitHub.")
                .ifBlank { "New Kindred release published on GitHub." }
            val htmlUrl = json.optString("html_url", "https://github.com/$repoSlug/releases/latest")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        apkUrl = downloadUrl
                        break
                    }
                }
            }
            val remoteBuild = extractBuildNumber(tagName, title)
            val hasUpdate = tagName.isNotBlank() && isRemoteReleaseNewer(
                tagName = tagName,
                title = title,
                localBuildNumber = localBuildNumber,
                localVersionName = localVersionName
            )
            if (!hasUpdate) {
                return@withContext UpdateCheckResult.UpToDate(
                    currentVersionLabel = "v$localVersionName (Build #$localBuildNumber)",
                    checkedSource = "GitHub ($repoSlug • Latest: $tagName)"
                )
            }
            UpdateCheckResult.Success(
                AppReleaseInfo(
                    sourceLabel = "GitHub Releases ($repoSlug)",
                    tagName = tagName,
                    title = title,
                    releaseNotes = body.take(400),
                    releasePageUrl = htmlUrl,
                    apkDownloadUrl = apkUrl,
                    remoteBuildNumber = remoteBuild,
                    isUpdateAvailable = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Unable to reach GitHub update server")
        } finally {
            connection?.disconnect()
        }
    }

    fun openExternalUpdateUrl(context: Context, rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun isNetworkConnected(context: Context): Boolean {
        return runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching false
            val network = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    fun shouldAutoCheckNow(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val lastMs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_AUTO_CHECK_MS, 0L)
        return (nowMs - lastMs) >= AUTO_CHECK_INTERVAL_MS
    }

    fun recordAutoCheckTimestamp(context: Context, nowMs: Long = System.currentTimeMillis()) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTO_CHECK_MS, nowMs)
            .apply()
    }

    fun getAppSigningSha256(context: Context): String {
        return runCatching {
            val pm = context.packageManager
            val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                val sigs = if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
                sigs?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return@runCatching "Unavailable"

            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            digest.joinToString(":") { "%02X".format(it) }
        }.getOrDefault("Unavailable")
    }
}
