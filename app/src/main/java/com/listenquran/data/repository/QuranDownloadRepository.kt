package com.listenquran.data.repository

import android.content.Context
import com.listenquran.data.local.dao.DownloadDao
import com.listenquran.data.local.entity.DownloadedSurahEntity
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadDao,
    private val quranRepository: QuranRepository,
) {
    private val downloadsDir: File
        get() = File(context.filesDir, "offline_downloads").apply {
            if (!exists()) mkdirs()
        }

    fun getAllDownloadedSurahs(): Flow<List<DownloadedSurahEntity>> {
        return downloadDao.getAllDownloadedSurahs()
    }

    fun isDownloaded(surahNumber: Int, reciterId: Int): Flow<Boolean> {
        return downloadDao.isDownloaded(surahNumber, reciterId)
    }

    suspend fun getDownloadedSurah(surahNumber: Int, reciterId: Int): DownloadedSurahEntity? {
        val entity = downloadDao.getDownloadedSurah(surahNumber, reciterId) ?: return null
        // Verify file still exists on disk
        if (File(entity.localFilePath).exists()) {
            return entity
        } else {
            downloadDao.deleteDownloadedSurah(surahNumber, reciterId)
            return null
        }
    }

    fun getTotalDownloadedBytes(): Flow<Long?> {
        return downloadDao.getTotalDownloadedBytes()
    }

    suspend fun downloadSurah(
        surah: Surah,
        reciter: Reciter,
        onProgress: (Int) -> Unit = {}
    ): Result<DownloadedSurahEntity> = withContext(Dispatchers.IO) {
        try {
            val audioData = quranRepository.getChapterAudio(reciter.id, surah.id)
                ?: return@withContext Result.failure(Exception("Audio URL not available"))

            val fileName = "surah_${surah.id}_reciter_${reciter.id}.mp3"
            val targetFile = File(downloadsDir, fileName)

            val request = Request.Builder()
                .url(audioData.audioUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code} downloading audio"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val contentLength = body.contentLength()
            var bytesReadTotal = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesReadTotal += read
                        if (contentLength > 0) {
                            val progress = ((bytesReadTotal * 100) / contentLength).toInt()
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            val entity = DownloadedSurahEntity(
                surahNumber = surah.id,
                surahName = surah.nameSimple,
                surahArabic = surah.nameArabic,
                reciterId = reciter.id,
                reciterName = reciter.englishName,
                localFilePath = targetFile.absolutePath,
                audioUrl = audioData.audioUrl,
                sizeBytes = targetFile.length(),
            )
            downloadDao.insertDownloadedSurah(entity)
            Result.success(entity)
        } catch (e: Exception) {
            android.util.Log.e("QuranDownloadRepo", "Error downloading surah ${surah.id}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDownload(surahNumber: Int, reciterId: Int) = withContext(Dispatchers.IO) {
        val existing = downloadDao.getDownloadedSurah(surahNumber, reciterId)
        if (existing != null) {
            try {
                File(existing.localFilePath).delete()
            } catch (_: Exception) {}
            downloadDao.deleteDownloadedSurah(surahNumber, reciterId)
        }
    }

    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        try {
            downloadsDir.listFiles()?.forEach { it.delete() }
            val cacheDir = File(context.filesDir, "offline_audio_cache")
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
        downloadDao.clearAllDownloads()
    }
}
