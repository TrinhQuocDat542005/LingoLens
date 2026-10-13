package com.quocdat.lingolens.lesson

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.quocdat.lingolens.assessment.core.PackedQuestion
import com.quocdat.lingolens.ui.learning.LocalLearningSession
import kotlinx.coroutines.launch

private fun lessonOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
fun LessonScreen(navController: NavController) {
    val account = LocalLearningSession.current
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val model: OfflineLessonScreenModel = viewModel(key = "lessons-${account.profile.id}", factory = OfflineLessonScreenModel.factory(
        account.container.lessonStudyRepository, account.profile.id, { !lessonOnline(context) }))
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val speechController = remember(account.profile.id, context) {
        LessonSpeechController(AndroidLessonSpeechEngine(context), lessonOnline(context))
    }
    val speech by speechController.state.collectAsState()
    DisposableEffect(speechController, lifecycle) {
        speechController.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> speechController.refreshVoices(lessonOnline(context))
                Lifecycle.Event.ON_STOP -> speechController.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speechController.close() }
    }
    LaunchedEffect(state.session?.id, state.stage, state.questionIndex, state.localRetry?.index) { speechController.stop() }
    LaunchedEffect(model) { model.load() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = {
            speechController.stop()
            if (state.stage == "CATALOG") navController.popBackStack()
            else if (state.stage == "RETRY_WRONG") model.finishWrongPractice()
            else model.backToCourses()
        }) { Text(when (state.stage) { "CATALOG" -> "Về trang chủ"; "RETRY_WRONG" -> "Về kết quả đã nộp"; else -> "Về lộ trình" }) }
        Text(state.pack?.title ?: "Học theo lộ trình", style = MaterialTheme.typography.headlineSmall)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (state.stage) {
            "CATALOG" -> {
                Text("XP lộ trình đã xác nhận: ${state.officialXp}", style = MaterialTheme.typography.titleMedium)
                Text("Tổng của các chặng đã tải; cập nhật khi có kết nối.")
                if (state.courses.isEmpty() && !state.busy) Text("Chưa có chặng đã tải. Kết nối mạng để tải lộ trình.")
                state.courses.forEach { course ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(course.title, style = MaterialTheme.typography.titleLarge)
                            if (course.demo) Text("DEMO • Nội dung thử nghiệm, chưa được rà soát độc lập")
                            Text(if (course.ready) "Đã tải đủ 3 bài • Có thể học offline" else "Đã tải ${course.downloaded}/3 bài • Cần tải đủ chặng trước khi học")
                            OutlinedButton(onClick = { scope.launch { model.download(course.id) } }, enabled = !state.busy) {
                                Text(if (course.ready) "Kiểm tra nội dung mới" else if (course.downloaded > 0) "Tiếp tục tải chặng" else "Tải chặng")
                            }
                            course.lessons.forEach { lesson ->
                                Text("Bài ${lesson.pack.position}: ${lesson.pack.title}", style = MaterialTheme.typography.titleMedium)
                                Text(when {
                                    lesson.confirmedPassed -> "Đã đạt • Server xác nhận"
                                    lesson.session?.phase == "BLOCKED" -> "Bị từ chối • Đáp án đã được giữ"
                                    lesson.session?.phase in setOf("QUEUED", "WAITING_PREREQUISITE") -> "Chờ server xác nhận"
                                    lesson.session?.phase == "CONFIRMED" -> "Server xác nhận • Chưa đạt"
                                    lesson.session != null -> "Đang học • Đã lưu trên máy"
                                    lesson.canOpen -> "Sẵn sàng"
                                    else -> "Cần vượt bài trước"
                                })
                                Button(onClick = { scope.launch { model.openLesson(course.id, lesson.pack.lessonId) } },
                                    enabled = !state.busy && (lesson.canOpen || lesson.session != null)) {
                                    Text(if (lesson.session == null) "Học bài ${lesson.pack.position}" else "Mở bài ${lesson.pack.position}")
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { scope.launch { model.load() } }, enabled = !state.busy) { Text("Tải lại lộ trình") }
            }
            "LEARN" -> state.pack?.let { pack ->
                if (pack.contentStatus == "DEMO") Text("DEMO • Nội dung thử nghiệm")
                Text(pack.introduction)
                Text("Từ trong bài", style = MaterialTheme.typography.titleLarge)
                pack.vocabularies.forEach { word ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${word.word} • ${word.translation}", style = MaterialTheme.typography.titleMedium)
                            Text("${word.phonetic} · ${word.partOfSpeech}"); Text(word.definition); Text(word.example)
                            TextButton(onClick = {
                                speechController.refreshVoices(lessonOnline(context)); speechController.speak(word.word)
                            }) { Text("Phát âm en-US") }
                        }
                    }
                }
                LessonVoiceGuidance(speechController, speech, activityContext)
                Text("Mẫu câu", style = MaterialTheme.typography.titleLarge)
                pack.sentencePatterns.forEach { Text("${it.pattern}\n${it.example}\n${it.translation}") }
                Button(onClick = model::showQuestions, enabled = !state.busy) { Text("Bắt đầu trả lời") }
            }
            "QUESTIONS" -> state.pack?.questions?.getOrNull(state.questionIndex)?.let { question ->
                Text("Câu ${state.questionIndex + 1}/${state.questionCount}", style = MaterialTheme.typography.titleMedium)
                LessonQuestionPanel(question, state.session!!.id, state.answers[question.id].orEmpty(),
                    state.feedback, question.id in state.fallbackQuestions, speechController, speech, activityContext,
                    enabled = !state.frozen, busy = state.busy,
                    onChoose = { answer -> scope.launch { model.choose(question.id, answer) } },
                    onCheck = model::checkAnswer, onFallback = { model.useFallback(question.id) })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { scope.launch { model.previous() } }, enabled = state.questionIndex > 0 && !state.busy) { Text("Câu trước") }
                    Button(onClick = { scope.launch { model.next() } }, enabled = model.hasAnswer(question) && state.feedback?.questionId == question.id && !state.busy) { Text("Tiếp tục") }
                }
            }
            "READY" -> {
                Text("Đã trả lời ${state.answers.size}/${state.questionCount} câu.")
                Text("Đạt bài từ 70%. Mỗi bài chỉ thưởng XP một lần.")
                Button(onClick = { scope.launch { model.submit() } }, enabled = !state.busy) { Text("Nộp bài") }
                OutlinedButton(onClick = { scope.launch { model.previous() } }, enabled = !state.busy) { Text("Xem lại đáp án") }
            }
            "SUMMARY" -> {
                Text("${state.correctCount}/${state.questionCount} câu đúng", style = MaterialTheme.typography.headlineMedium)
                when (state.session?.phase) {
                    "CONFIRMED" -> {
                        Text(if (state.passed == true) "Đã đạt • Server xác nhận" else "Chưa đạt • Server xác nhận")
                        state.confirmedResult?.let { Text("+${it.xpAwarded} XP đã xác nhận") }
                        Text("XP lộ trình đã xác nhận: ${state.officialXp}")
                        Button(onClick = { scope.launch { model.restart() } }, enabled = !state.busy) { Text("Học lại bài này") }
                    }
                    "BLOCKED" -> {
                        Text("Server từ chối • Kết quả trên máy chưa được công nhận", color = MaterialTheme.colorScheme.error)
                        Text("Mã lỗi: ${state.syncError ?: "LESSON_REQUEST_INVALID"}. Đáp án được giữ; bài này không tự gửi lại.")
                    }
                    else -> {
                        Text(if (state.passed == true) "Đạt tạm • Chờ server xác nhận" else "Chưa đạt tạm • Chờ server xác nhận")
                        if (state.session?.phase == "WAITING_PREREQUISITE") Text("Server đang chờ xác nhận bài trước; sẽ thử lại cùng lượt nộp.")
                        Text("Chưa có XP chính thức cho lượt nộp này.")
                        OutlinedButton(onClick = { scope.launch { model.retry() } }, enabled = !state.busy) { Text("Yêu cầu đồng bộ lại") }
                    }
                }
                if (state.canRetryWrong) OutlinedButton(onClick = model::startWrongPractice, enabled = !state.busy) { Text("Luyện lại câu sai — không thay kết quả đã nộp") }
                val next = state.courses.firstOrNull { it.id == state.pack?.courseId }?.lessons?.firstOrNull { it.pack.position == (state.pack?.position ?: 0) + 1 }
                if (next != null && next.canOpen) Button(onClick = { scope.launch { model.openLesson(next.pack.courseId, next.pack.lessonId) } }, enabled = !state.busy) { Text("Học bài ${next.pack.position}") }
                Text("Đáp án đã lưu", style = MaterialTheme.typography.titleLarge)
                state.pack?.questions?.forEach { question ->
                    Text(question.prompt)
                    val raw = state.answers[question.id]
                    Text("Bạn trả lời: ${if (question.type == "FILL_GAP") raw ?: "Chưa trả lời" else question.options.firstOrNull { it.key == raw }?.label ?: "Chưa trả lời"}")
                    Text(question.explanation, style = MaterialTheme.typography.bodySmall)
                }
            }
            "RETRY_WRONG" -> state.localRetry?.let { retry ->
                val question = state.pack?.questions?.firstOrNull { it.id == retry.questionIds.getOrNull(retry.index) }
                Text("Luyện lại — không thay kết quả đã nộp", style = MaterialTheme.typography.titleLarge)
                Text("Kết quả đã nộp: ${state.correctCount}/${state.questionCount} câu đúng. Không cấp thêm XP.")
                if (question != null) {
                    Text("Câu luyện ${retry.index + 1}/${retry.questionIds.size}")
                    LessonQuestionPanel(question, "retry-${state.session!!.id}", retry.answers[question.id].orEmpty(),
                        retry.feedback, question.id in state.fallbackQuestions, speechController, speech, activityContext,
                        enabled = true, busy = false, onChoose = { model.chooseRetry(question.id, it) },
                        onCheck = model::checkRetry, onFallback = { model.useFallback(question.id) })
                    Button(onClick = model::nextRetry, enabled = retry.feedback != null) {
                        Text(if (retry.index < retry.questionIds.lastIndex) "Câu luyện tiếp" else "Về kết quả đã nộp")
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonQuestionPanel(question: PackedQuestion, sessionKey: String, answer: String,
    feedback: LessonQuestionFeedback?, fallback: Boolean, controller: LessonSpeechController,
    speech: LessonSpeechState, context: Context, enabled: Boolean, busy: Boolean,
    onChoose: (String) -> Unit, onCheck: () -> Unit, onFallback: () -> Unit) {
    Text(question.prompt, style = MaterialTheme.typography.titleLarge)
    if (question.type == "LISTEN_CHOICE") {
        if (fallback) {
            Text("Câu thay thế không cần nghe", style = MaterialTheme.typography.titleMedium)
            Text(question.fallbackPrompt.orEmpty())
        } else {
            Button(onClick = {
                if (speech.speaking) controller.stop()
                else { controller.refreshVoices(lessonOnline(context)); controller.speak(question.speechText.orEmpty()) }
            }, enabled = speech.status != LessonSpeechStatus.INITIALIZING) {
                Text(if (speech.speaking) "Dừng nghe" else "Nghe en-US")
            }
            LessonVoiceGuidance(controller, speech, context)
            OutlinedButton(onClick = { controller.stop(); onFallback() }) { Text("Dùng câu thay thế không cần nghe") }
        }
    }
    if (question.type == "FILL_GAP") {
        // Keep immediate editor state while Room serializes every accepted raw edit.
        var draft by remember(sessionKey, question.id) { mutableStateOf(answer) }
        var tooLong by remember(sessionKey, question.id) { mutableStateOf(false) }
        OutlinedTextField(value = draft, onValueChange = {
            tooLong = it.length > 500
            if (!tooLong) { draft = it; onChoose(it) }
        }, enabled = enabled, modifier = Modifier.fillMaxWidth(), label = { Text("Điền từ hoặc cụm từ") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            isError = tooLong, supportingText = { if (tooLong) Text("Tối đa 500 ký tự. Nội dung vượt giới hạn chưa được lưu.") })
        Button(onClick = onCheck, enabled = enabled && !busy && draft.isNotBlank() && !tooLong) { Text("Kiểm tra câu này") }
    } else question.options.forEach { option ->
        OutlinedButton(onClick = { onChoose(option.key) }, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
            Text((if (answer == option.key) "✓ " else "") + option.label)
        }
    }
    feedback?.takeIf { it.questionId == question.id }?.let {
        Text(if (it.correct) "Đúng" else "Chưa đúng", color = if (it.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(it.explanation)
    }
}

@Composable
private fun LessonVoiceGuidance(controller: LessonSpeechController, speech: LessonSpeechState, context: Context) {
    var installError by remember { mutableStateOf<String?>(null) }
    if (speech.status == LessonSpeechStatus.INITIALIZING) Text("Đang khởi tạo giọng en-US…")
    if (speech.status == LessonSpeechStatus.READY) Text(if (speech.selectedVoice?.requiresNetwork == true) "Giọng en-US cần mạng" else "Giọng en-US trên máy • Dùng ngoại tuyến")
    speech.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (speech.status in setOf(LessonSpeechStatus.MISSING_VOICE, LessonSpeechStatus.NETWORK_REQUIRED, LessonSpeechStatus.ERROR)) {
        TextButton(onClick = {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply { controller.defaultEnginePackage?.let { setPackage(it) } }
            try { context.startActivity(intent); installError = null }
            catch (_: ActivityNotFoundException) { installError = "Mở Cài đặt → Text-to-Speech để cài giọng English (United States)." }
            catch (_: SecurityException) { installError = "Mở Cài đặt → Text-to-Speech để cài giọng English (United States)." }
        }) { Text("Cài giọng English (United States)") }
    }
    installError?.let { Text(it) }
}
