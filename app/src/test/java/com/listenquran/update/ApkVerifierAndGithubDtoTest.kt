package com.listenquran.update

import com.listenquran.data.remote.dto.GithubAssetDto
import com.listenquran.data.remote.dto.GithubReleaseDto
import com.listenquran.data.remote.dto.UpdateManifestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkVerifierAndGithubDtoTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `enforces trusted github HTTPS download hosts only`() {
        assertTrue(ApkVerifier.isAllowedDownloadUrl("https://github.com/tokitauhid/listen-quran/releases/download/v1.4.0/ListenQuran-v1.4.0.apk"))
        assertTrue(ApkVerifier.isAllowedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset-123/app.apk"))
        assertFalse(ApkVerifier.isAllowedDownloadUrl("http://github.com/tokitauhid/listen-quran/app.apk"))
        assertFalse(ApkVerifier.isAllowedDownloadUrl("https://evil-mirror.example.com/ListenQuran-v1.4.0.apk"))
    }

    @Test
    fun `verifies SHA-256 digest matching github release format`() {
        val file = tempFolder.newFile("sample.apk")
        file.writeText("listen-quran-ota-payload", Charsets.UTF_8)

        val actualHex = ApkVerifier.computeSha256Hex(file)
        assertTrue(ApkVerifier.verifySha256(file, "sha256:$actualHex"))
        assertTrue(ApkVerifier.verifySha256(file, actualHex.uppercase()))
        assertFalse(ApkVerifier.verifySha256(file, "sha256:${"0".repeat(64)}"))
        assertFalse(ApkVerifier.verifySha256(file, null))
    }

    @Test
    fun `maps newer non-draft non-prerelease release and respects 12h auto check throttle`() {
        val dto = GithubReleaseDto(
            tagName = "v1.5.0",
            name = "Listen Quran v1.5.0",
            body = "Changelog text",
            htmlUrl = "https://github.com/tokitauhid/listen-quran/releases/tag/v1.5.0",
            draft = false,
            prerelease = false,
            assets = listOf(
                GithubAssetDto(
                    name = "ListenQuran-v1.5.0.apk",
                    size = 5_200_000L,
                    digest = "sha256:${"a".repeat(64)}",
                    contentType = "application/vnd.android.package-archive",
                    browserDownloadUrl = "https://github.com/tokitauhid/listen-quran/releases/download/v1.5.0/ListenQuran-v1.5.0.apk",
                )
            ),
        )

        val mapped = GithubReleaseMapper.mapIfNewer(dto, "1.4.0")
        assertNotNull(mapped)
        assertEquals("1.5.0", mapped!!.versionName)
        assertEquals("ListenQuran-v1.5.0.apk", mapped.assetName)

        assertNull(GithubReleaseMapper.mapIfNewer(dto, "1.5.0"))
        assertNull(GithubReleaseMapper.mapIfNewer(dto.copy(prerelease = true), "1.4.0"))
        assertNull(GithubReleaseMapper.mapIfNewer(dto.copy(draft = true), "1.4.0"))

        val now = 1_000_000_000L
        assertFalse(GithubReleaseMapper.shouldAutoCheck(autoCheckEnabled = true, lastCheckedAtMs = now - 60_000L, nowMs = now))
        assertTrue(GithubReleaseMapper.shouldAutoCheck(autoCheckEnabled = true, lastCheckedAtMs = now - (12L * 3600L * 1000L + 1L), nowMs = now))
        assertFalse(GithubReleaseMapper.shouldAutoCheck(autoCheckEnabled = false, lastCheckedAtMs = 0L, nowMs = now))
    }

    @Test
    fun `parses CDN update manifest JSON and compares versionName and versionCode`() {
        val rawJson = """
            {
              "tagName": "v1.4.1",
              "versionName": "1.4.1",
              "versionCode": 10,
              "title": "Listen Quran v1.4.1",
              "changelog": "- Fixed background Wi-Fi cast focus handling",
              "htmlUrl": "https://github.com/tokitauhid/listen-quran/releases/tag/v1.4.1",
              "publishedAt": "2026-11-27T19:30:00Z",
              "assetName": "ListenQuran-v1.4.1.apk",
              "downloadUrl": "https://github.com/tokitauhid/listen-quran/releases/download/v1.4.1/ListenQuran-v1.4.1.apk",
              "sizeBytes": 5300000,
              "sha256": "sha256:${"b".repeat(64)}"
            }
        """.trimIndent()
        val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            .adapter(UpdateManifestDto::class.java)
        val dto = adapter.fromJson(rawJson)!!

        val newerSemVer = GithubReleaseMapper.mapManifestIfNewer(dto, "1.4.0", 9)
        assertNotNull(newerSemVer)
        assertEquals("1.4.1", newerSemVer!!.versionName)

        // Same versionName with higher versionCode counts as newer
        val higherCode = GithubReleaseMapper.mapManifestIfNewer(dto, "1.4.1", 9)
        assertNotNull(higherCode)

        // Same versionName and same/higher installed versionCode is up to date
        assertNull(GithubReleaseMapper.mapManifestIfNewer(dto, "1.4.1", 10))
        assertNull(GithubReleaseMapper.mapManifestIfNewer(dto, "1.5.0", 11))
    }
}
