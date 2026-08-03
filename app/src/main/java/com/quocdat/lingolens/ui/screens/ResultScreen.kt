package com.quocdat.lingolens.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.quocdat.lingolens.camera.CapturedImageRepository
import com.quocdat.lingolens.model.LearnedWord
import com.quocdat.lingolens.navigation.Screen
import com.quocdat.lingolens.recognition.RecognitionCandidate
import com.quocdat.lingolens.recognition.RecognitionResult
import com.quocdat.lingolens.recognition.RecognitionUiState
import com.quocdat.lingolens.recognition.RecognitionViewModel
import com.quocdat.lingolens.recognition.SupportedVocabulary
import com.quocdat.lingolens.service.FakeDictionaryService
import com.quocdat.lingolens.service.FakeWordRepository
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(navController: NavController, word: String, imageUri: String? = null) {
    val context = LocalContext.current
    val uri = remember(imageUri) { imageUri?.let(Uri::parse) }
    val imageRepository = remember(context) { CapturedImageRepository(context.applicationContext) }
    val recognitionViewModel: RecognitionViewModel = viewModel(factory = RecognitionViewModel.Factory(context))
    val recognitionState by recognitionViewModel.state.collectAsState()
    var selectedWord by remember(imageUri) { mutableStateOf(if (uri == null) word else null) }
    var showManualPicker by remember { mutableStateOf(false) }
    val learnedWord = selectedWord?.let { remember(it) { FakeDictionaryService.lookupWord(it) } }
    val imageState by produceState<CapturedBitmapState>(
        initialValue = if (uri == null) CapturedBitmapState.NotRequested else CapturedBitmapState.Loading,
        uri
    ) {
        value = uri?.let {
            withContext(Dispatchers.IO) {
                runCatching { decodeOrientedBitmap(context, it) }
                    .fold(
                        onSuccess = { bitmap -> bitmap?.let(CapturedBitmapState::Ready) ?: CapturedBitmapState.Error },
                        onFailure = { CapturedBitmapState.Error }
                    )
            }
        } ?: CapturedBitmapState.NotRequested
    }

    LaunchedEffect(recognitionState) {
        val success = recognitionState as? RecognitionUiState.Success ?: return@LaunchedEffect
        if (!success.result.needsConfirmation) selectedWord = success.result.primary?.word
    }

    LaunchedEffect(uri) {
        uri?.let(recognitionViewModel::analyze)
    }

    fun deleteCapturedImage() = uri?.let(imageRepository::delete)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nhận diện vật thể", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại camera")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(innerPadding).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CapturedImageCard(imageState, uri != null)

            if (uri != null) {
                RecognitionSection(
                    state = recognitionState,
                    selectedWord = selectedWord,
                    showManualPicker = showManualPicker,
                    onAnalyze = { recognitionViewModel.analyze(uri) },
                    onRetry = { recognitionViewModel.retry(); selectedWord = null },
                    onSelect = { selectedWord = it; showManualPicker = false },
                    onToggleManual = { showManualPicker = !showManualPicker }
                )
            }

            learnedWord?.let { WordResultContent(it, context) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { deleteCapturedImage(); navController.popBackStack() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Chụp lại")
                    Spacer(Modifier.width(6.dp))
                    Text("Chụp lại")
                }
                if (learnedWord != null) {
                    Button(
                        onClick = {
                            FakeWordRepository.saveWord(learnedWord)
                            deleteCapturedImage()
                            Toast.makeText(context, "Đã lưu từ '${learnedWord.word}'", Toast.LENGTH_SHORT).show()
                            navController.navigate(Screen.MyWords.route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Done, "Lưu")
                        Spacer(Modifier.width(6.dp))
                        Text("Lưu từ")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            deleteCapturedImage()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Hủy") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CapturedImageCard(state: CapturedBitmapState, hasImage: Boolean) {
    if (!hasImage) return
    Card(Modifier.fillMaxWidth().aspectRatio(4f / 3f), shape = RoundedCornerShape(24.dp)) {
        when (state) {
            is CapturedBitmapState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = "Ảnh vật thể vừa chụp",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            CapturedBitmapState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không thể đọc ảnh. Hãy chụp lại.", Modifier.padding(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecognitionSection(
    state: RecognitionUiState,
    selectedWord: String?,
    showManualPicker: Boolean,
    onAnalyze: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onToggleManual: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                RecognitionUiState.Idle -> {
                    Text("Ảnh đã sẵn sàng", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Nhận diện chạy trực tiếp trên thiết bị và không tải ảnh lên máy chủ.")
                    Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
                        Text("Phân tích bằng ML Kit")
                    }
                }
                RecognitionUiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(26.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Đang nhận diện…", fontWeight = FontWeight.Bold)
                            Text("Phân tích nhãn và độ tin cậy", fontSize = 13.sp)
                        }
                    }
                }
                is RecognitionUiState.Success -> RecognitionCandidates(
                    result = state.result,
                    selectedWord = selectedWord,
                    onSelect = onSelect
                )
                is RecognitionUiState.NoMatch -> {
                    Text("Chưa tìm thấy vật thể phù hợp", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("AI chưa ghép được ảnh với bộ từ vựng LingoLens. Hãy chọn từ thủ công hoặc chụp lại rõ hơn.")
                    RawLabels(state.rawLabels)
                }
                is RecognitionUiState.Error -> {
                    Text("Nhận diện thất bại", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(state.message)
                    Button(onClick = onRetry) { Text("Thử lại") }
                }
            }

            if (state != RecognitionUiState.Loading && state != RecognitionUiState.Idle) {
                TextButton(onClick = onToggleManual) {
                    Text(if (showManualPicker) "Ẩn danh sách từ" else "Kết quả chưa đúng? Chọn từ khác")
                }
            }
            if (showManualPicker || state is RecognitionUiState.NoMatch) {
                ManualWordPicker(onSelect)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecognitionCandidates(
    result: RecognitionResult,
    selectedWord: String?,
    onSelect: (String) -> Unit
) {
    val primary = result.primary ?: return
    Text(
        if (result.needsConfirmation) "AI chưa hoàn toàn chắc chắn" else "Đã nhận diện vật thể",
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        color = if (result.needsConfirmation) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    )
    Text("Kết quả tốt nhất: ${primary.word.uppercase()} · ${primary.confidence.asPercent()}")
    Text("Chọn kết quả đúng:", fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        result.candidates.forEach { candidate ->
            SuggestionChip(
                onClick = { onSelect(candidate.word) },
                label = { Text("${candidate.word} ${candidate.confidence.asPercent()}") },
                icon = if (selectedWord == candidate.word) {
                    { Icon(Icons.Default.Done, null) }
                } else null
            )
        }
    }
    RawLabels(result.rawLabels)
}

@Composable
private fun RawLabels(labels: List<RecognitionCandidate>) {
    if (labels.isEmpty()) return
    Text(
        "Nhãn gốc: " + labels.take(3).joinToString { "${it.sourceLabel} ${it.confidence.asPercent()}" },
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualWordPicker(onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val normalized = query.trim().lowercase(Locale.US)
    val visibleWords = remember(normalized) {
        SupportedVocabulary.words.filter { it.contains(normalized) }.take(12)
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it.filter { char -> char.isLetter() || char == ' ' || char == '-' } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Nhập hoặc tìm từ tiếng Anh") },
        singleLine = true
    )
    if (normalized.length >= 2) {
        Button(onClick = { onSelect(normalized) }, modifier = Modifier.fillMaxWidth()) {
            Text("Dùng từ “$normalized”")
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visibleWords.forEach { candidate ->
            AssistChip(onClick = { onSelect(candidate) }, label = { Text(candidate) })
        }
    }
}

@Composable
private fun WordResultContent(learnedWord: LearnedWord, context: Context) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TỪ VỰNG ĐÃ CHỌN", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(learnedWord.word.uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("/${learnedWord.word}/", fontStyle = FontStyle.Italic)
            Spacer(Modifier.height(8.dp))
            Text(learnedWord.translation, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                Toast.makeText(context, "Phát âm mẫu: ${learnedWord.word}", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.PlayArrow, "Phát âm")
                Spacer(Modifier.width(6.dp))
                Text("Phát âm")
            }
        }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(onClick = {}, label = { Text(learnedWord.partOfSpeech) })
                SuggestionChip(onClick = {}, label = { Text("Level ${learnedWord.level}") })
            }
            Text("Định nghĩa", fontWeight = FontWeight.Bold)
            Text(learnedWord.definition)
            HorizontalDivider()
            Text("Ví dụ B1", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            Text(learnedWord.exampleSentence)
            Text("Ví dụ B2", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            Text(learnedWord.exampleSentenceB2)
        }
    }
}

private fun Float.asPercent(): String = "${(this * 100).toInt()}%"

private fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1600)
    options.inJustDecodeBounds = false
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    val orientation = context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply {
        postRotate(degrees)
    }, true).also { if (it !== bitmap) bitmap.recycle() }
}

internal fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (width / sample > maxSide || height / sample > maxSide) sample *= 2
    return sample
}

private sealed interface CapturedBitmapState {
    data object NotRequested : CapturedBitmapState
    data object Loading : CapturedBitmapState
    data class Ready(val bitmap: Bitmap) : CapturedBitmapState
    data object Error : CapturedBitmapState
}
