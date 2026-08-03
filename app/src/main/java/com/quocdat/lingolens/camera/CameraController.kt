package com.quocdat.lingolens.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

class CameraController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    val hasFlashUnit: Boolean
        get() = camera?.cameraInfo?.hasFlashUnit() == true

    val flashEnabled: Boolean
        get() = camera?.cameraInfo?.torchState?.value == 1

    fun start(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onReady: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindUseCases(provider, previewView, lifecycleOwner)
            }.onSuccess { onReady() }.onFailure(onError)
        }, mainExecutor)
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner
    ) {
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        if (!provider.hasCamera(selector)) {
            throw IllegalStateException("Thiết bị không có camera phù hợp.")
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
    }

    fun capture(
        outputFile: File,
        onSuccess: () -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(ImageCaptureException(ImageCapture.ERROR_INVALID_CAMERA, "Camera chưa sẵn sàng.", null))
            return
        }
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onSuccess()
            override fun onError(exception: ImageCaptureException) = onError(exception)
        })
    }

    fun toggleFlash(onChanged: (Boolean) -> Unit, onError: (Throwable) -> Unit) {
        val activeCamera = camera ?: return onError(IllegalStateException("Camera chưa sẵn sàng."))
        if (!activeCamera.cameraInfo.hasFlashUnit()) {
            return onError(IllegalStateException("Camera này không hỗ trợ đèn flash."))
        }
        val enabled = !flashEnabled
        activeCamera.cameraControl.enableTorch(enabled).addListener(
            { runCatching { onChanged(enabled) }.onFailure(onError) },
            mainExecutor
        )
    }

    fun switchCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onReady: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val provider = cameraProvider ?: return onError(IllegalStateException("Camera chưa sẵn sàng."))
        val target = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val selector = CameraSelector.Builder().requireLensFacing(target).build()
        if (!provider.hasCamera(selector)) {
            return onError(IllegalStateException("Thiết bị không có camera còn lại."))
        }
        lensFacing = target
        runCatching { bindUseCases(provider, previewView, lifecycleOwner) }
            .onSuccess { onReady() }
            .onFailure(onError)
    }

    fun stop() {
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
    }
}
