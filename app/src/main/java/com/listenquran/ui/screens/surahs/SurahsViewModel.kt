package com.listenquran.ui.screens.surahs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listenquran.data.repository.QuranRepository
import com.listenquran.domain.model.Surah
import com.listenquran.player.AudioPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class SurahSortOrder {
    MUSHAF,     // 1..114
    REVELATION  // Revelation chronology (e.g. Al-Alaq first)
}

@Immutable
data class SurahTileUi(
    val surah: Surah,
    val id: Int,
    val numberText: String,
    val nameSimple: String,
    val nameArabic: String,
    val subtitle: String,
    val contentDescription: String,
    val revelationOrder: Int,
)

sealed interface SurahsUiState {
    data object Loading : SurahsUiState
    data class Success(
        val surahs: List<SurahTileUi>,
        val totalCount: Int,
        val filteredCount: Int,
    ) : SurahsUiState
    data class Error(val message: String) : SurahsUiState
}

@HiltViewModel
class SurahsViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val playerManager: AudioPlayerManager,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow(SurahSortOrder.MUSHAF)
    val isGridMode = MutableStateFlow(true)

    // Only emit when the currently playing Surah ID changes to prevent recomposition on playback position ticks
    val currentPlayingSurahId: StateFlow<Int?> = playerManager.playbackState
        .map { it.currentSurah?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val allSurahsFlow: Flow<List<Surah>> = quranRepository.getSurahs()
        .catch { emit(emptyList()) }

    val uiState: StateFlow<SurahsUiState> = combine(
        allSurahsFlow,
        searchQuery,
        sortOrder
    ) { surahs, query, sort ->
        if (surahs.isEmpty()) {
            SurahsUiState.Loading
        } else {
            val precomputed = surahs.map { s ->
                val origin = if (s.isMakki) "Meccan" else "Medinan"
                SurahTileUi(
                    surah = s,
                    id = s.id,
                    numberText = "${s.id}",
                    nameSimple = s.nameSimple,
                    nameArabic = s.nameArabic,
                    subtitle = "${s.versesCount} Ayahs • $origin",
                    contentDescription = "Surah ${s.id}, ${s.nameSimple}, ${s.versesCount} ayahs, $origin",
                    revelationOrder = s.revelationOrder
                )
            }

            val filtered = if (query.isBlank()) {
                precomputed
            } else {
                val q = query.trim()
                precomputed.filter {
                    it.nameSimple.contains(q, ignoreCase = true) ||
                    it.surah.translatedName.contains(q, ignoreCase = true) ||
                    it.surah.nameComplex.contains(q, ignoreCase = true) ||
                    it.id.toString() == q ||
                    it.nameArabic.contains(q)
                }
            }

            val sorted = when (sort) {
                SurahSortOrder.MUSHAF -> filtered.sortedBy { it.id }
                SurahSortOrder.REVELATION -> filtered.sortedBy { it.revelationOrder }
            }

            SurahsUiState.Success(
                surahs = sorted,
                totalCount = surahs.size,
                filteredCount = sorted.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SurahsUiState.Loading)

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun setSortOrder(order: SurahSortOrder) {
        sortOrder.value = order
    }

    fun toggleGridMode() {
        isGridMode.value = !isGridMode.value
    }
}
