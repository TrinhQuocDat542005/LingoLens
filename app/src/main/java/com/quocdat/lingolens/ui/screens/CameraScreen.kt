package com.quocdat.lingolens.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.quocdat.lingolens.camera.CameraController
import com.quocdat.lingolens.camera.CameraPreview
import com.quocdat.lingolens.camera.CameraUiState
import com.quocdat.lingolens.camera.CameraViewModel
import com.quocdat.lingolens.camera.CapturedImageRepository
import com.quocdat.lingolens.navigation.Screen

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) { PreviewView(context) }
    val controller = remember(context) { CameraController(context) }
    val cameraViewModel: CameraViewModel = viewModel(
        factory = CameraViewModel.Factory(remember(context) { CapturedImageRepository(context.applicationContext) })
    )
    val uiState by cameraViewModel.uiState.collectAsState()
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedPermission = true
        permissionGranted = granted
        if (granted) cameraViewModel.openingCamera() else cameraViewModel.requestPermission()
    }

    LaunchedEffect(permissionGranted, lifecycleOwner) {
        if (permissionGranted) {
            cameraViewModel.openingCamera()
            controller.start(
                previewView = previewView,
                lifecycleOwner = lifecycleOwner,
                onReady = {
                    cameraViewModel.ready(
                        controller.lensFacing,
                        controller.flashEnabled,
                        controller.hasFlashUnit
                    )
                },
                onError = cameraViewModel::error
            )
        } else {
            cameraViewModel.requestPermission()
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState) {
        val captured = uiState as? CameraUiState.Captured ?: return@LaunchedEffect
        cameraViewModel.openingCamera()
        navController.navigate(Screen.Result.createRoute(imageUri = captured.imageUri.toString()))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            CameraPreview(previewView)
            ScannerOverlay(
                state = uiState,
                onBack = { navController.popBackStack() },
                onCapture = {
                    if (uiState !is CameraUiState.Ready) return@ScannerOverlay
                    val file = cameraViewModel.createTempFile()
                    cameraViewModel.capturing()
                    controller.capture(
                        outputFile = file,
                        onSuccess = { cameraViewModel.captured(file) },
                        onError = {
                            file.delete()
                            cameraViewModel.error(it)
                        }
                    )
                },
                onToggleFlash = {
                    controller.toggleFlash(
                        onChanged = {
                            cameraViewModel.ready(
                                controller.lensFacing,
                                it,
                                controller.hasFlashUnit
                            )
                        },
                        onError = cameraViewModel::error
                    )
                },
                onSwitchCamera = {
                    cameraViewModel.openingCamera()
                    controller.switchCamera(
                        previewView,
                        lifecycleOwner,
                        onReady = {
                            cameraViewModel.ready(
                                controller.lensFacing,
                                controller.flashEnabled,
                                controller.hasFlashUnit
                            )
                        },
                        onError = cameraViewModel::error
                    )
                },
                onRetry = {
                    cameraViewModel.openingCamera()
                    controller.start(
                        previewView,
                        lifecycleOwner,
                        onReady = {
                            cameraViewModel.ready(
                                controller.lensFacing,
                                controller.flashEnabled,
                                controller.hasFlashUnit
                            )
                        },
                        onError = cameraViewModel::error
                    )
                }
            )
        } else {
            val showSettings = hasRequestedPermission &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    context.findActivity(),
                    Manifest.permission.CAMERA
                )
            PermissionContent(
                showSettings = showSettings,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Không tìm thấy Activity để yêu cầu quyền camera.")
}

@Composable
private fun ScannerOverlay(
    state: CameraUiState,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                .padding(vertical = 120.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                "VÙNG CHỤP VẬT THỂ",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = .55f), CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = Color.White)
            }
            Text("LingoLens Camera", color = Color.White, fontWeight = FontWeight.Bold)
            val ready = state as? CameraUiState.Ready
            Button(
                onClick = onToggleFlash,
                enabled = ready?.hasFlashUnit == true,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = .55f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) {
                Text(if (ready?.flashEnabled == true) "FLASH ON" else "FLASH", fontSize = 11.sp)
            }
        }

        when (state) {
            CameraUiState.OpeningCamera -> CameraStatus("Đang mở camera…")
            CameraUiState.Capturing -> CameraStatus("Đang chụp ảnh…")
            is CameraUiState.Error -> ErrorCard(state.message, onRetry)
            else -> Unit
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Đặt một vật thể vào giữa khung hình", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                IconButton(
                    onClick = onSwitchCamera,
                    enabled = state is CameraUiState.Ready,
                    modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = .55f), CircleShape)
                ) {
                    Icon(Icons.Default.Refresh, "Đổi camera", tint = Color.White)
                }
                Box(
                    Modifier
                        .size(82.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(if (state is CameraUiState.Ready) Color.White else Color.Gray)
                        .clickable(enabled = state is CameraUiState.Ready, onClick = onCapture)
                )
                Spacer(Modifier.size(52.dp))
            }
        }
    }
}

@Composable
private fun CameraStatus(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .8f)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Không thể sử dụng camera", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.White.copy(alpha = .8f))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Thử lại") }
        }
    }
}

@Composable
private fun PermissionContent(
    showSettings: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Cho phép sử dụng camera", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "LingoLens cần camera để chụp đồ vật và nhận diện từ vựng. Ảnh chỉ được lưu tạm trên thiết bị.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f)
            )
            if (showSettings) {
                Text("Quyền camera đã bị tắt. Hãy bật lại trong cài đặt ứng dụng.")
                Button(onClick = onOpenSettings) { Text("Mở cài đặt") }
            } else {
                Button(onClick = onRequestPermission) { Text("Cấp quyền camera") }
            }
            OutlinedButton(onClick = onBack) { Text("Để sau") }
        }
    }
}
