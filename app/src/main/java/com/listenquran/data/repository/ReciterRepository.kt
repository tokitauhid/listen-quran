package com.listenquran.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.listenquran.data.local.dao.ReciterDao
import com.listenquran.data.local.entity.ReciterEntity
import com.listenquran.data.remote.QuranApiService
import com.listenquran.domain.model.Reciter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReciterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: QuranApiService,
    private val reciterDao: ReciterDao,
) {
    companion object {
        val defaultReciters = listOf(
            ReciterEntity(7, "مشاري راشد العفاسي", "Mishari Rashid al-`Afasy", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true, audioServer = "https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/"),
            ReciterEntity(2, "عبد الباسط عبد الصمد", "AbdulBaset AbdulSamad", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true, audioServer = "https://download.quranicaudio.com/qdc/abdul_baset/murattal/"),
            ReciterEntity(1, "عبد الباسط عبد الصمد", "AbdulBaset AbdulSamad", "Mujawwad", "Hafs 'an 'Asim", null, 114, isFavorite = false, audioServer = "https://download.quranicaudio.com/qdc/abdul_baset/mujawwad/"),
            ReciterEntity(3, "عبد الرحمن السديس", "Abdur-Rahman as-Sudais", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true, audioServer = "https://download.quranicaudio.com/qdc/abdurrahmaan_as_sudais/murattal/"),
            ReciterEntity(4, "أبو بكر الشاطري", "Abu Bakr al-Shatri", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = false, audioServer = "https://download.quranicaudio.com/qdc/abu_bakr_ash-shaatree/murattal/"),
            ReciterEntity(5, "هاني الرفاعي", "Hani ar-Rifai", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = false, audioServer = "https://download.quranicaudio.com/qdc/haani_ar_rifaa3ee/murattal/"),
            ReciterEntity(6, "محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true, audioServer = "https://download.quranicaudio.com/qdc/khalil_al_husary/murattal/"),
            ReciterEntity(12, "محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "Muallim", "Hafs 'an 'Asim", null, 114, isFavorite = false, audioServer = "https://download.quranicaudio.com/qdc/khalil_al_husary/muallim/"),
            ReciterEntity(8, "محمد صديق المنشاوي", "Mohamed Siddiq al-Minshawi", "Mujawwad", "Hafs 'an 'Asim", null, 114, isFavorite = false, audioServer = "https://download.quranicaudio.com/qdc/siddiq_minshawi/mujawwad/"),
            ReciterEntity(9, "محمد صديق المنشاوي", "Mohamed Siddiq al-Minshawi", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true, audioServer = "https://download.quranicaudio.com/qdc/siddiq_minshawi/murattal/"),
            ReciterEntity(10, "سعود الشريم", "Sa`ud ash-Shuraym", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = false, audioServer = "https://download.quranicaudio.com/qdc/sa3d_al-ghaamidi/complete/"),
            ReciterEntity(11, "محمد محمود الطبلاوي", "Mohamed al-Tablawi", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = false, audioServer = null),
        )
    }

    private var cachedAssetReciters: List<ReciterEntity>? = null

    private suspend fun loadRecitersFromAssets(): List<ReciterEntity> = withContext(Dispatchers.IO) {
        cachedAssetReciters?.let { return@withContext it }
        try {
            val jsonString = context.assets.open("reciters.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<ReciterEntity>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ReciterEntity(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        englishName = obj.getString("englishName"),
                        style = obj.optString("style", "Murattal"),
                        qiraah = obj.optString("qiraah", "Hafs 'an 'Asim"),
                        photoUrl = if (obj.has("photoUrl") && !obj.isNull("photoUrl")) obj.getString("photoUrl") else null,
                        surahsCount = obj.optInt("surahsCount", 114),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        audioServer = if (obj.has("audioServer") && !obj.isNull("audioServer")) obj.getString("audioServer") else null
                    )
                )
            }
            cachedAssetReciters = list
            list
        } catch (e: Exception) {
            android.util.Log.e("ReciterRepository", "Error reading reciters.json asset", e)
            emptyList()
        }
    }

    suspend fun initializeReciters() = withContext(Dispatchers.IO) {
        val count = reciterDao.getRecitersCount()
        if (count < 50) {
            val assetReciters = loadRecitersFromAssets()
            if (assetReciters.isNotEmpty()) {
                reciterDao.insertReciters(assetReciters)
            } else if (count == 0) {
                reciterDao.insertReciters(defaultReciters)
            }
        }
        refreshReciters()
    }

    suspend fun refreshReciters() = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getRecitations(language = "en")
            val apiRecitations = response.recitations
            if (apiRecitations.isNotEmpty()) {
                val assetReciters = loadRecitersFromAssets()
                val entities = apiRecitations.map { dto ->
                    val assetMatch = assetReciters.find { it.id == dto.id }
                        ?: defaultReciters.find { it.id == dto.id }
                    val existing = reciterDao.getReciterById(dto.id)
                    val rawStyle = dto.style ?: assetMatch?.style ?: "Murattal"
                    val normalizedStyle = rawStyle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    ReciterEntity(
                        id = dto.id,
                        name = assetMatch?.name ?: dto.reciterName,
                        englishName = dto.translatedName?.name ?: dto.reciterName,
                        style = normalizedStyle,
                        qiraah = assetMatch?.qiraah ?: "Hafs 'an 'Asim",
                        photoUrl = assetMatch?.photoUrl,
                        surahsCount = assetMatch?.surahsCount ?: 114,
                        isFavorite = existing?.isFavorite ?: assetMatch?.isFavorite ?: false,
                        audioServer = assetMatch?.audioServer
                    )
                }
                reciterDao.insertReciters(entities)
            }
        } catch (e: Exception) {
            android.util.Log.w("ReciterRepository", "Background reciters refresh failed", e)
        }
    }

    fun getPagedReciters(query: String = "", style: String = ""): Flow<PagingData<Reciter>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 10,
                enablePlaceholders = false,
                initialLoadSize = 30
            ),
            pagingSourceFactory = {
                val cleanQuery = query.trim()
                val cleanStyle = if (style.isBlank() || style == "All") "" else style.trim()
                reciterDao.getFilteredPagedReciters(cleanQuery, cleanStyle)
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    fun getReciters(): Flow<List<Reciter>> {
        return reciterDao.getAllReciters().map { list ->
            if (list.isNotEmpty()) {
                list.map { it.toDomain() }
            } else {
                defaultReciters.map { it.toDomain() }
            }
        }
    }

    fun getDefaultReciter(): Reciter = defaultReciters.first().toDomain()

    suspend fun getReciterById(id: Int): Reciter? {
        val entity = reciterDao.getReciterById(id)
        if (entity != null) return entity.toDomain()
        return defaultReciters.find { it.id == id }?.toDomain()
    }

    suspend fun toggleFavorite(reciterId: Int, isFavorite: Boolean) {
        reciterDao.updateFavorite(reciterId, isFavorite)
    }

    fun getFavorites(): Flow<List<Reciter>> {
        return reciterDao.getFavoriteReciters().map { list -> list.map { it.toDomain() } }
    }

    private fun ReciterEntity.toDomain() = Reciter(
        id = id,
        name = name,
        englishName = englishName,
        style = style,
        qiraah = qiraah,
        photoUrl = photoUrl,
        surahsCount = surahsCount,
        isFavorite = isFavorite,
        audioServer = audioServer,
    )
}
