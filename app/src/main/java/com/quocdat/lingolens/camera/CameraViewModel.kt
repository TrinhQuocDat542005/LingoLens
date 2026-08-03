package com.quocdat.lingolens.camera

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class CameraViewModel(
    private val imageRepository: CapturedImageRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.RequestingPermission)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun requestPermission() {
        _uiState.value = CameraUiState.RequestingPermission
    }

    fun openingCamera() {
        _uiState.value = CameraUiState.OpeningCamera
    }

    fun ready(
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        flashEnabled: Boolean = false,
        hasFlashUnit: Boolean = false
    ) {
        _uiState.value = CameraUiState.Ready(lensFacing, flashEnabled, hasFlashUnit)
    }

    fun capturing() {
        if (_uiState.value is CameraUiState.Ready) _uiState.value = CameraUiState.Capturing
    }

    fun captured(file: File) {
        _uiState.value = CameraUiState.Captured(imageRepository.getUri(file))
    }

    fun error(throwable: Throwable) {
        _uiState.value = CameraUiState.Error(
            throwable.message?.takeIf(String::isNotBlank) ?: "Không thể sử dụng camera. Vui lòng thử lại."
        )
    }

    fun createTempFile(): File = imageRepository.createTempImageFile()

    fun deleteImage(uri: Uri) {
        imageRepository.delete(uri)
    }

    class Factory(private val repository: CapturedImageRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CameraViewModel::class.java))
            return CameraViewModel(repository) as T
        }
    }
}
