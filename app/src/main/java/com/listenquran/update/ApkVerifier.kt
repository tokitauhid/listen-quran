package com.listenquran.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

sealed interface ArchiveVerifyResult {
    data object Valid : ArchiveVerifyResult
    data object WrongPackage : ArchiveVerifyResult
    data object VersionNotNewer : ArchiveVerifyResult
    data object SignatureMismatch : ArchiveVerifyResult
    data object CorruptArchive : ArchiveVerifyResult
}

object ApkVerifier {

    fun isAllowedDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        return host == "github.com" ||
            host == "api.github.com" ||
            host.endsWith(".githubusercontent.com")
    }

    fun normalizeSha256Digest(rawDigest: String?): String? {
        val cleaned = rawDigest?.trim()
            ?.removePrefix("sha256:")
            ?.removePrefix("SHA256:")
            ?.lowercase(Locale.US)
            ?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        return cleaned
    }

    fun computeSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }

    fun verifySha256(file: File, expectedDigest: String?): Boolean {
        if (!file.exists() || !file.isFile) return false
        val expectedHex = normalizeSha256Digest(expectedDigest) ?: return false
        val actualHex = runCatching { computeSha256Hex(file) }.getOrNull() ?: return false
        return MessageDigest.isEqual(
            expectedHex.toByteArray(Charsets.US_ASCII),
            actualHex.toByteArray(Charsets.US_ASCII),
        )
    }

    @Suppress("DEPRECATION")
    fun verifyApkArchive(
        context: Context,
        apkFile: File,
        expectedPackageName: String,
        currentVersionCode: Long,
    ): ArchiveVerifyResult {
        if (!apkFile.exists() || apkFile.length() == 0L) return ArchiveVerifyResult.CorruptArchive
        val pm = context.packageManager

        val archiveInfo: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            }
        } catch (_: Exception) {
            null
        } ?: return ArchiveVerifyResult.CorruptArchive

        if (archiveInfo.packageName != expectedPackageName) {
            return ArchiveVerifyResult.WrongPackage
        }

        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (archiveVersionCode <= currentVersionCode) {
            return ArchiveVerifyResult.VersionNotNewer
        }

        val installedInfo: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    expectedPackageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(expectedPackageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageInfo(expectedPackageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: Exception) {
            return ArchiveVerifyResult.CorruptArchive
        }

        val archiveCerts = extractSignerDigests(archiveInfo)
        val installedCerts = extractSignerDigests(installedInfo)
        if (archiveCerts.isEmpty() || installedCerts.isEmpty() || archiveCerts.intersect(installedCerts).isEmpty()) {
            return ArchiveVerifyResult.SignatureMismatch
        }

        return ArchiveVerifyResult.Valid
    }

    @Suppress("DEPRECATION")
    private fun extractSignerDigests(info: PackageInfo): Set<String> {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val bytesList: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            val rawSigners = signingInfo?.apkContentsSigners ?: signingInfo?.signingCertificateHistory
            rawSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }
        return bytesList.map { raw ->
            sha256.digest(raw).joinToString("") { "%02x".format(Locale.US, it) }
        }.toSet()
    }
}
