package com.listenquran.data.repository

import com.listenquran.data.local.dao.ReciterDao
import com.listenquran.data.local.entity.ReciterEntity
import com.listenquran.data.remote.QuranApiService
import com.listenquran.domain.model.Reciter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReciterRepository @Inject constructor(
    private val apiService: QuranApiService,
    private val reciterDao: ReciterDao,
) {
    private val defaultReciters = listOf(
        ReciterEntity(7, "مشاري راشد العفاسي", "Mishari Rashid al-`Afasy", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true),
        ReciterEntity(2, "عبد الباسط عبد الصمد", "AbdulBaset AbdulSamad", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true),
        ReciterEntity(1, "عبد الباسط عبد الصمد", "AbdulBaset AbdulSamad", "Mujawwad", "Hafs 'an 'Asim", null, 114, isFavorite = false),
        ReciterEntity(3, "عبد الرحمن السديس", "Abdur-Rahman as-Sudais", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true),
        ReciterEntity(4, "أبو بكر الشاطري", "Abu Bakr al-Shatri", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = false),
        ReciterEntity(5, "هاني الرفاعي", "Hani ar-Rifai", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = false),
        ReciterEntity(6, "محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true),
        ReciterEntity(12, "ماهر المعيقلي", "Maher Al-Muaiqly", "Murattal", "Hafs 'an 'Asim", null, 114, isFavorite = true),
    )

    fun getReciters(): Flow<List<Reciter>> = flow {
        reciterDao.getAllReciters().collect { entities ->
            if (entities.isNotEmpty()) {
                emit(entities.map { it.toDomain() })
            } else {
                // Seed default popular reciters immediately
                reciterDao.insertReciters(defaultReciters)
                emit(defaultReciters.map { it.toDomain() })

                // Then sync full list from network in background
                try {
                    val response = apiService.getRecitations()
                    val fetched = response.recitations.map { r ->
                        ReciterEntity(
                            id = r.id,
                            name = r.translatedName?.name ?: r.reciterName,
                            englishName = r.reciterName,
                            style = r.style ?: "Murattal",
                            qiraah = "Hafs 'an 'Asim",
                            photoUrl = null,
                            surahsCount = 114,
                            isFavorite = r.id == 7,
                        )
                    }
                    reciterDao.insertReciters(fetched)
                    emit(fetched.map { it.toDomain() })
                } catch (_: Exception) {
                    // Fallback to seeded defaults
                }
            }
        }
    }

    suspend fun getReciterById(id: Int): Reciter? {
        val entity = reciterDao.getReciterById(id)
        return entity?.toDomain() ?: defaultReciters.find { it.id == id }?.toDomain()
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
    )
}
