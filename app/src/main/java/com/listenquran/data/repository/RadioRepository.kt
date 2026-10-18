package com.listenquran.data.repository

import com.listenquran.domain.model.CuratedRadioStations
import com.listenquran.domain.model.RadioStation
import com.listenquran.domain.model.RadioStationType
import com.listenquran.domain.model.Reciter
import com.listenquran.util.ReciterImages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val reciterRepository: ReciterRepository,
) {
    /**
     * Emits all available Quran.com radio stations:
     * 1. Official Quran.com Curated Stations (Popular Recitations, Yaseen/Waqiah/Mulk, Al-Kahf, Juz Amma).
     * 2. Reciter Radio Stations (24/7 continuous stream per reciter).
     */
    fun getRadioStations(): Flow<List<RadioStation>> {
        return reciterRepository.getReciters().map { reciters ->
            val uniqueReciters = reciters.distinctBy { it.name.replace(" ", "") }
            val reciterStations = uniqueReciters.map { reciter ->
                reciter.toRadioStation()
            }
            CuratedRadioStations + reciterStations
        }
    }

    /**
     * Returns curated stations only.
     */
    fun getCuratedStations(): List<RadioStation> = CuratedRadioStations

    /**
     * Resolves a specific station by its unique ID.
     */
    fun getStationById(id: String): Flow<RadioStation?> {
        return getRadioStations().map { stations ->
            stations.find { it.id == id }
        }
    }

    private fun Reciter.toRadioStation(): RadioStation {
        val photo = photoUrl ?: ReciterImages.getPhotoUrl(id, englishName)
        return RadioStation(
            id = "reciter_$id",
            name = "$englishName Radio",
            description = "24/7 continuous recitation by $englishName",
            type = RadioStationType.RECITER,
            bannerUrl = photo,
            reciterId = id,
            reciterName = englishName,
            targetSurahs = (1..114).toList(),
            poolReciterIds = listOf(id),
            city = style?.ifBlank { "Hafs" } ?: "Hafs",
            isLive = false
        )
    }
}
