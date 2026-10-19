package com.listenquran.data.repository

import com.listenquran.data.local.dao.TranslationDao
import com.listenquran.data.local.entity.TranslationEntity
import com.listenquran.data.remote.QuranApiService
import com.listenquran.data.remote.dto.ChapterTranslationResponse
import com.listenquran.data.remote.dto.TranslationMetaDto
import com.listenquran.data.remote.dto.VerseTranslationDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TranslationRepositoryTest {

    private class FakeTranslationDao : TranslationDao {
        val stored = mutableListOf<TranslationEntity>()

        override fun getTranslationsForSurah(surahNumber: Int, translationId: Int): Flow<List<TranslationEntity>> {
            return emptyFlow()
        }

        override suspend fun getTranslationsForSurahOnce(surahNumber: Int, translationId: Int): List<TranslationEntity> {
            return stored.filter { it.surahNumber == surahNumber && it.translationId == translationId }
        }

        override suspend fun insertTranslations(translations: List<TranslationEntity>) {
            stored.addAll(translations)
        }

        override suspend fun getTranslationCountForSurah(surahNumber: Int, translationId: Int): Int {
            return stored.count { it.surahNumber == surahNumber && it.translationId == translationId }
        }
    }

    private class FakeQuranApiService(
        private val shouldFailWithOffline: Boolean = false,
    ) : QuranApiService {
        override suspend fun getChapters(language: String) = throw UnsupportedOperationException()
        override suspend fun getRecitations(language: String) = throw UnsupportedOperationException()
        override suspend fun getChapterRecitation(reciterId: Int, chapterId: Int, segments: Boolean) = throw UnsupportedOperationException()
        override suspend fun getVersesByChapter(chapterId: Int, language: String, words: Boolean, translations: String, fields: String, perPage: Int) = throw UnsupportedOperationException()

        override suspend fun getTranslationsByChapter(resourceId: Int, chapterNumber: Int): ChapterTranslationResponse {
            if (shouldFailWithOffline) {
                throw IOException("Unable to resolve host api.quran.com: No address associated with hostname")
            }
            return ChapterTranslationResponse(
                translations = listOf(
                    VerseTranslationDto(resourceId = 20, text = "In the name of Allāh,<sup foot_note=195932>1</sup> the Entirely Merciful"),
                    VerseTranslationDto(resourceId = 20, text = "[All] praise is [due] to Allāh")
                ),
                meta = TranslationMetaDto(
                    translationName = "Saheeh International",
                    authorName = "Saheeh International"
                )
            )
        }
    }

    @Test
    fun `returns cached translations immediately from Room without calling network`() = runBlocking {
        val dao = FakeTranslationDao()
        dao.stored.add(
            TranslationEntity(
                surahNumber = 1,
                ayahNumber = 1,
                verseKey = "1:1",
                translationId = 20,
                translationName = "Saheeh International",
                text = "In the name of Allāh, the Entirely Merciful"
            )
        )
        // API service that throws an error if called
        val apiService = FakeQuranApiService(shouldFailWithOffline = true)
        val repository = TranslationRepository(apiService, dao)

        val states = repository.getTranslationsForSurah(1).toList()
        assertEquals(2, states.size)
        assertTrue(states[0] is TranslationState.Loading)
        assertTrue(states[1] is TranslationState.Success)

        val success = states[1] as TranslationState.Success
        assertEquals(1, success.surahNumber)
        assertEquals("In the name of Allāh, the Entirely Merciful", success.translations[1])
    }

    @Test
    fun `fetches from API when not in Room, sanitizes, caches, and emits Success`() = runBlocking {
        val dao = FakeTranslationDao()
        val apiService = FakeQuranApiService(shouldFailWithOffline = false)
        val repository = TranslationRepository(apiService, dao)

        val states = repository.getTranslationsForSurah(1).toList()
        assertTrue(states[0] is TranslationState.Loading)
        assertTrue(states[1] is TranslationState.Success)

        val success = states[1] as TranslationState.Success
        assertEquals("In the name of Allāh, the Entirely Merciful", success.translations[1])
        assertEquals("[All] praise is [due] to Allāh", success.translations[2])

        // Verify stored in Room
        assertEquals(2, dao.stored.size)
        assertEquals(1, dao.stored[0].surahNumber)
        assertEquals(20, dao.stored[0].translationId)
    }

    @Test
    fun `handles offline state gracefully without throwing exception`() = runBlocking {
        val dao = FakeTranslationDao()
        val apiService = FakeQuranApiService(shouldFailWithOffline = true)
        val repository = TranslationRepository(apiService, dao)

        val states = repository.getTranslationsForSurah(1).toList()
        assertTrue(states[0] is TranslationState.Loading)
        assertTrue(states[1] is TranslationState.Error)

        val error = states[1] as TranslationState.Error
        assertTrue(error.isOffline)
        assertEquals("Translation unavailable offline", error.message)
    }
}
