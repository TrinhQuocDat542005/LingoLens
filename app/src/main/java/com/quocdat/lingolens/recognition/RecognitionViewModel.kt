package com.quocdat.lingolens.recognition

import android.content.Context
import android.net.Uri
import com.quocdat.lingolens.data.repository.RecognitionRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecognitionViewModel(
    private val recognitionService: ObjectRecognitionService,
    private val repository: RecognitionRepository,
    private val accountId: Long
) : ViewModel() {
    private val _state = MutableStateFlow<RecognitionUiState>(RecognitionUiState.Idle)
    val state: StateFlow<RecognitionUiState> = _state.asStateFlow()
    private val _syncState = MutableStateFlow<RecognitionSyncState>(RecognitionSyncState.Idle)
    val syncState: StateFlow<RecognitionSyncState> = _syncState.asStateFlow()
    private var historyId: String? = null

    fun analyze(uri: Uri) {
        if (_state.value == RecognitionUiState.Loading) return
        _state.value = RecognitionUiState.Loading
        viewModelScope.launch {
            runCatching { recognitionService.recognize(uri) }
                .onSuccess { result ->
                    _state.value = if (result.candidates.isEmpty()) {
                        RecognitionUiState.NoMatch(
                            rawLabels = result.rawLabels,
                            imageQuality = result.imageQuality,
                            inferenceTimeMs = result.inferenceTimeMs
                        )
                    } else {
                        RecognitionUiState.Success(result)
                    }
                    result.primary?.let { primary ->
                        repository.save(accountId, primary.word, primary.confidence, result.engine.name)
                            .onSuccess { history -> historyId = history; _syncState.value = RecognitionSyncState.Queued }
                            .onFailure { error -> _syncState.value = RecognitionSyncState.SyncFailed(error.message ?: "Không thể đồng bộ lịch sử") }
                    }
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    _state.value = RecognitionUiState.Error(
                        it.message?.takeIf(String::isNotBlank)
                            ?: "Không thể nhận diện ảnh. Hãy chụp lại trong điều kiện đủ sáng."
                    )
                }
        }
    }

    fun retry() {
        _state.value = RecognitionUiState.Idle
        _syncState.value = RecognitionSyncState.Idle
        historyId = null
    }

    fun reportCorrection(expectedLabel: String, note: String?) {
        if (_syncState.value == RecognitionSyncState.Reporting || _syncState.value == RecognitionSyncState.ReportQueued) return
        val id = historyId ?: run {
            _syncState.value = RecognitionSyncState.SyncFailed("Lịch sử chưa được lưu trên thiết bị, chưa thể gửi báo cáo")
            return
        }
        _syncState.value = RecognitionSyncState.Reporting
        viewModelScope.launch {
            repository.report(accountId, id, expectedLabel, note)
                .onSuccess { _syncState.value = RecognitionSyncState.ReportQueued }
                .onFailure { _syncState.value = RecognitionSyncState.SyncFailed(it.message ?: "Không thể gửi báo cáo") }
        }
    }

    override fun onCleared() {
        recognitionService.close()
    }

    class Factory(context: Context, private val repository: RecognitionRepository, private val accountId: Long) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecognitionViewModel::class.java))
            return RecognitionViewModel(ObjectRecognitionService(appContext), repository, accountId) as T
        }
    }
}

sealed interface RecognitionSyncState {
    data object Idle : RecognitionSyncState
    data object Queued : RecognitionSyncState
    data object Reporting : RecognitionSyncState
    data object ReportQueued : RecognitionSyncState
    data class SyncFailed(val message: String) : RecognitionSyncState
}
