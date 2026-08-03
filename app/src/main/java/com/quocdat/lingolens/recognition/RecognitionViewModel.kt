package com.quocdat.lingolens.recognition

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecognitionViewModel(
    private val recognitionService: ObjectRecognitionService
) : ViewModel() {
    private val _state = MutableStateFlow<RecognitionUiState>(RecognitionUiState.Idle)
    val state: StateFlow<RecognitionUiState> = _state.asStateFlow()

    fun analyze(uri: Uri) {
        if (_state.value == RecognitionUiState.Loading) return
        viewModelScope.launch {
            _state.value = RecognitionUiState.Loading
            runCatching { recognitionService.recognize(uri) }
                .onSuccess { result ->
                    _state.value = if (result.candidates.isEmpty()) {
                        RecognitionUiState.NoMatch(result.rawLabels)
                    } else {
                        RecognitionUiState.Success(result)
                    }
                }
                .onFailure {
                    _state.value = RecognitionUiState.Error(
                        it.message?.takeIf(String::isNotBlank)
                            ?: "Không thể nhận diện ảnh. Hãy chụp lại trong điều kiện đủ sáng."
                    )
                }
        }
    }

    fun retry() {
        _state.value = RecognitionUiState.Idle
    }

    override fun onCleared() {
        recognitionService.close()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecognitionViewModel::class.java))
            return RecognitionViewModel(ObjectRecognitionService(appContext)) as T
        }
    }
}
