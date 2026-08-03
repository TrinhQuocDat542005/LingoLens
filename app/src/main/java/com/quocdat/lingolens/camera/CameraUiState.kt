package com.quocdat.lingolens.camera

import android.net.Uri

sealed interface CameraUiState {
    data object RequestingPermission : CameraUiState
    data object OpeningCamera : CameraUiState
    data class Ready(
        val lensFacing: Int,
        val flashEnabled: Boolean,
        val hasFlashUnit: Boolean
    ) : CameraUiState
    data object Capturing : CameraUiState
    data class Captured(val imageUri: Uri) : CameraUiState
    data class Error(val message: String) : CameraUiState
}
