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
