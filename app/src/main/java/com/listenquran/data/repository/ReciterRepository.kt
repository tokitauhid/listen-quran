package com.listenquran.data.repository

import android.content.Context
import com.listenquran.data.local.dao.ReciterDao
import com.listenquran.data.local.entity.ReciterEntity
import com.listenquran.data.remote.QuranApiService
import com.listenquran.domain.model.Reciter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
            ReciterEntity(12, "ماهر المعيقلي", "Maher Al-Muaiqly", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true, audioServer = "https://download.quranicaudio.com/qdc/khalil_al_husary/muallim/"),
        )
    }

    private suspend fun loadRecitersFromAssets(): List<ReciterEntity> = withContext(Dispatchers.IO) {
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
            list
        } catch (e: Exception) {
            android.util.Log.e("ReciterRepository", "Error reading reciters.json asset", e)
            emptyList()
        }
    }

    fun getReciters(): Flow<List<Reciter>> = flow {
        reciterDao.getAllReciters().collect { entities ->
            if (entities.isNotEmpty() && entities.size > 20) {
                emit(entities.map { it.toDomain() })
            } else {
                // Seed 255 reciters from bundled assets
                val assetReciters = loadRecitersFromAssets()
                val toInsert = if (assetReciters.isNotEmpty()) assetReciters else defaultReciters
                reciterDao.insertReciters(toInsert)
                emit(toInsert.map { it.toDomain() })
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
