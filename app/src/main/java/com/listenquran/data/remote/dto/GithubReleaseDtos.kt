package com.listenquran.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GithubReleaseDto(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "draft") val draft: Boolean = false,
    @Json(name = "prerelease") val prerelease: Boolean = false,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "assets") val assets: List<GithubAssetDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GithubAssetDto(
    @Json(name = "name") val name: String,
    @Json(name = "size") val size: Long = 0L,
    @Json(name = "digest") val digest: String? = null,
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class UpdateManifestDto(
    @Json(name = "tagName") val tagName: String,
    @Json(name = "versionName") val versionName: String = "",
    @Json(name = "versionCode") val versionCode: Int = 0,
    @Json(name = "title") val title: String? = null,
    @Json(name = "changelog") val changelog: String? = null,
    @Json(name = "htmlUrl") val htmlUrl: String = "",
    @Json(name = "publishedAt") val publishedAt: String? = null,
    @Json(name = "assetName") val assetName: String,
    @Json(name = "downloadUrl") val downloadUrl: String,
    @Json(name = "sizeBytes") val sizeBytes: Long = 0L,
    @Json(name = "sha256") val sha256: String? = null,
)
