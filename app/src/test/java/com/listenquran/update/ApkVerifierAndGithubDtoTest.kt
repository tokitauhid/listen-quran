package com.listenquran.update

import com.listenquran.data.remote.dto.GithubAssetDto
import com.listenquran.data.remote.dto.GithubReleaseDto
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
}
