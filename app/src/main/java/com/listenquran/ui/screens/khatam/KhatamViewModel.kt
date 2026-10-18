package com.listenquran.ui.screens.khatam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listenquran.data.local.khatam.KhatamEntity
import com.listenquran.data.repository.khatam.ImportMode
import com.listenquran.data.repository.khatam.ImportResult
import com.listenquran.data.repository.khatam.KhatamGoalStatus
import com.listenquran.data.repository.khatam.KhatamTrackerRepository
import com.listenquran.domain.model.khatam.AyahRangeSet
import com.listenquran.domain.model.khatam.QuranAyahMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class KhatamViewModel @Inject constructor(
    private val repository: KhatamTrackerRepository
) : ViewModel() {

    val activeKhatam: StateFlow<KhatamEntity?> = repository.activeKhatam
    val rangeSet: StateFlow<AyahRangeSet> = repository.activeRangeSet
    val todayAyahsRead: StateFlow<Int> = repository.todayAyahsRead
    val goalStatus: StateFlow<KhatamGoalStatus> = repository.goalStatus
    val allKhatams: StateFlow<List<KhatamEntity>> = repository.allKhatams
    val emergencyWarning: StateFlow<String?> = repository.emergencyWarning

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun dismissMessage() {
        _userMessage.value = null
    }

    fun markJuz(juzNumber: Int) {
        viewModelScope.launch {
            repository.markJuz(juzNumber)
        }
    }

    fun unmarkJuz(juzNumber: Int) {
        viewModelScope.launch {
            val range = QuranAyahMetadata.juzRange(juzNumber)
            repository.unmarkRange(range.first, range.last)
        }
    }

    fun markSurah(surahNumber: Int) {
        viewModelScope.launch {
            repository.markSurah(surahNumber)
        }
    }

    fun unmarkSurah(surahNumber: Int) {
        viewModelScope.launch {
            val startAyah = QuranAyahMetadata.toGlobalAyah(surahNumber, 1)
            val count = QuranAyahMetadata.surahAyahCounts.getOrElse(surahNumber - 1) { 1 }
            val endAyah = startAyah + count - 1
            repository.unmarkRange(startAyah, endAyah)
        }
    }

    fun markRange(startAyah: Int, endAyah: Int) {
        viewModelScope.launch {
            repository.markRange(startAyah, endAyah, source = "MANUAL")
        }
    }

    fun unmarkRange(startAyah: Int, endAyah: Int) {
        viewModelScope.launch {
            repository.unmarkRange(startAyah, endAyah)
        }
    }

    fun startNewKhatam(title: String, targetDays: Int?) {
        viewModelScope.launch {
            repository.startNewKhatam(title = title, targetDays = targetDays)
            _userMessage.value = "New Khatam started: $title"
        }
    }

    fun updateGoal(targetDays: Int?) {
        viewModelScope.launch {
            repository.updateGoal(targetDays = targetDays, targetDate = null)
        }
    }

    fun exportBackup(outputStream: OutputStream) {
        viewModelScope.launch {
            val success = repository.exportBackup(outputStream)
            _userMessage.value = if (success) "Backup exported successfully" else "Failed to export backup"
        }
    }

    fun importBackup(inputStream: InputStream, mode: ImportMode = ImportMode.MERGE) {
        viewModelScope.launch {
            val result = repository.importBackup(inputStream, mode)
            _userMessage.value = when (result) {
                is ImportResult.Success -> "Restored ${result.importedEventsCount} events successfully"
                is ImportResult.Failure -> "Import failed: ${result.reason}"
            }
        }
    }
}
