package com.listenquran.data.remote

import com.listenquran.data.remote.dto.GithubReleaseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface GithubUpdateService {
    @Headers(
        "Accept: application/vnd.github+json",
        "X-GitHub-Api-Version: 2022-11-28",
        "User-Agent: ListenQuran-Android"
    )
    @GET("repos/tokitauhid/listen-quran/releases/latest")
    suspend fun getLatestRelease(
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<GithubReleaseDto>
}
