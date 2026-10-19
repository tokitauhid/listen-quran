package com.listenquran.data.remote

import com.listenquran.data.remote.dto.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface QuranApiService {
    @GET("chapters")
    suspend fun getChapters(
        @Query("language") language: String = "en",
    ): ChaptersResponse

    @GET("resources/recitations")
    suspend fun getRecitations(
        @Query("language") language: String = "en",
    ): RecitationsResponse

    @GET("chapter_recitations/{reciter_id}/{chapter_id}")
    suspend fun getChapterRecitation(
        @Path("reciter_id") reciterId: Int,
        @Path("chapter_id") chapterId: Int,
        @Query("segments") segments: Boolean = true,
    ): ChapterAudioResponse

    @GET("verses/by_chapter/{chapter_id}")
    suspend fun getVersesByChapter(
        @Path("chapter_id") chapterId: Int,
        @Query("language") language: String = "en",
        @Query("words") words: Boolean = true,
        @Query("translations") translations: String = "131", // Clear Quran (Mustafa Khattab)
        @Query("fields") fields: String = "text_uthmani,chapter_id",
        @Query("per_page") perPage: Int = 300,
    ): VersesResponse

    @GET("quran/translations/{resource_id}")
    suspend fun getTranslationsByChapter(
        @Path("resource_id") resourceId: Int = 20, // Saheeh International
        @Query("chapter_number") chapterNumber: Int,
    ): ChapterTranslationResponse
}
