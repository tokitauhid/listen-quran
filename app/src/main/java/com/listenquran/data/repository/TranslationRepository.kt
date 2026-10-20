package com.listenquran.data.repository

import com.listenquran.data.local.dao.TranslationDao
import com.listenquran.data.local.entity.TranslationEntity
import com.listenquran.data.remote.QuranApiService
import com.listenquran.util.TranslationSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TranslationState {
    data object Loading : TranslationState
    data class Success(
        val surahNumber: Int,
        val translations: Map<Int, String>, // ayahNumber -> sanitized translation
        val translationName: String = "Saheeh International"
    ) : TranslationState
    data class Error(
        val message: String,
        val isOffline: Boolean
    ) : TranslationState
}

@Singleton
class TranslationRepository @Inject constructor(
    private val apiService: QuranApiService,
    private val translationDao: TranslationDao,
) {
    companion object {
        const val SAHEEH_INTERNATIONAL_ID = 20
        const val DEFAULT_TRANSLATION_NAME = "Saheeh International"
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    fun getTranslationsForSurah(surahNumber: Int): Flow<TranslationState> = flow {
        emit(TranslationState.Loading)

        // 1. Check Room cache first
        val cached = translationDao.getTranslationsForSurahOnce(surahNumber, SAHEEH_INTERNATIONAL_ID)
        if (cached.isNotEmpty()) {
            val map = cached.associate { it.ayahNumber to it.text }
            emit(TranslationState.Success(surahNumber, map, DEFAULT_TRANSLATION_NAME))
            prefetchNextSurah(surahNumber + 1)
            return@flow
        }

        // 2. Fetch from Quran.com v4
        try {
            val response = apiService.getTranslationsByChapter(
                resourceId = SAHEEH_INTERNATIONAL_ID,
                chapterNumber = surahNumber
            )
            val translationName = response.meta?.translationName ?: DEFAULT_TRANSLATION_NAME
            val entities = response.translations.mapIndexed { index, dto ->
                val ayahNumber = index + 1
                val sanitized = TranslationSanitizer.sanitize(dto.text)
                TranslationEntity(
                    surahNumber = surahNumber,
                    ayahNumber = ayahNumber,
                    verseKey = "$surahNumber:$ayahNumber",
                    translationId = SAHEEH_INTERNATIONAL_ID,
                    translationName = translationName,
                    text = sanitized
                )
            }

            if (entities.isNotEmpty()) {
                translationDao.insertTranslations(entities)
                val map = entities.associate { it.ayahNumber to it.text }
                emit(TranslationState.Success(surahNumber, map, translationName))
                prefetchNextSurah(surahNumber + 1)
            } else {
                emit(TranslationState.Error("No translations returned", isOffline = false))
            }
        } catch (e: Exception) {
            val isOffline = e is IOException || e.message?.contains("Unable to resolve host", ignoreCase = true) == true
            val msg = if (isOffline) "Translation unavailable without internet" else (e.message ?: "Failed to load translation")
            emit(TranslationState.Error(msg, isOffline = isOffline))
        }
    }.flowOn(Dispatchers.IO)

    private fun prefetchNextSurah(nextSurahNumber: Int) {
        if (nextSurahNumber > 114) return
        repositoryScope.launch {
            try {
                val count = translationDao.getTranslationCountForSurah(nextSurahNumber, SAHEEH_INTERNATIONAL_ID)
                if (count > 0) return@launch // already cached

                val response = apiService.getTranslationsByChapter(
                    resourceId = SAHEEH_INTERNATIONAL_ID,
                    chapterNumber = nextSurahNumber
                )
                val translationName = response.meta?.translationName ?: DEFAULT_TRANSLATION_NAME
                val entities = response.translations.mapIndexed { index, dto ->
                    val ayahNumber = index + 1
                    TranslationEntity(
                        surahNumber = nextSurahNumber,
                        ayahNumber = ayahNumber,
                        verseKey = "$nextSurahNumber:$ayahNumber",
                        translationId = SAHEEH_INTERNATIONAL_ID,
                        translationName = translationName,
                        text = TranslationSanitizer.sanitize(dto.text)
                    )
                }
                if (entities.isNotEmpty()) {
                    translationDao.insertTranslations(entities)
                }
            } catch (_: Exception) {
                // Background prefetch error ignored
            }
        }
    }
}
