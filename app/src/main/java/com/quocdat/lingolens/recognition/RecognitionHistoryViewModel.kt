package com.quocdat.lingolens.recognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quocdat.lingolens.data.remote.dto.RecognitionHistoryDto
import com.quocdat.lingolens.data.repository.RecognitionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecognitionHistoryViewModel(private val repository: RecognitionRepository) : ViewModel() {
    private val _state = MutableStateFlow<RecognitionHistoryState>(RecognitionHistoryState.Loading)
    val state: StateFlow<RecognitionHistoryState> = _state.asStateFlow()

    init { refresh() }
    fun refresh() = viewModelScope.launch {
        _state.value = RecognitionHistoryState.Loading
        repository.history()
            .onSuccess { _state.value = RecognitionHistoryState.Ready(it) }
            .onFailure { _state.value = RecognitionHistoryState.Error(it.message ?: "Không thể tải lịch sử") }
    }

    class Factory(private val repository: RecognitionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecognitionHistoryViewModel::class.java))
            return RecognitionHistoryViewModel(repository) as T
        }
    }
}

sealed interface RecognitionHistoryState {
    data object Loading : RecognitionHistoryState
    data class Ready(val items: List<RecognitionHistoryDto>) : RecognitionHistoryState
    data class Error(val message: String) : RecognitionHistoryState
}
