package com.quocdat.lingolens.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.quocdat.lingolens.data.repository.ReviewProgress
import com.quocdat.lingolens.review.core.*
import com.quocdat.lingolens.ui.learning.LocalLearningSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(navController: NavController) {
    val account = LocalLearningSession.current
    val repository = account.container.reviewRepository
    var id by rememberSaveable(account.profile.id) { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<ReviewProgress?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun perform(action: suspend () -> ReviewProgress) {
        if (busy) return
        busy = true
        scope.launch {
            try { progress = action().also { id = it.session.id }; error = null }
            catch (ex: CancellationException) { throw ex }
            catch (ex: Exception) { error = "Không thể tải phiên ôn. Hãy thử lại." }
            finally { busy = false }
        }
    }
    LaunchedEffect(account.profile.id) { perform { repository.start(account.profile.id, id) } }
    Scaffold(topBar = { TopAppBar(title = { Text("Ôn tập từ vựng") }, navigationIcon = {
        TextButton(onClick = { navController.popBackStack() }) { Text("Đóng") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); Button(onClick = { perform { repository.start(account.profile.id, id) } }) { Text("Thử lại") } }
            val current = progress
            if (current == null) { if (busy) CircularProgressIndicator(); return@Column }
            if (current.word == null) {
                Text(if (current.total == 0) "Chưa có từ đến hạn" else "Đã hoàn thành phiên ôn", style = MaterialTheme.typography.headlineMedium)
                Text("${current.answers.size} lượt ôn • ${current.answers.count { it.grade != "AGAIN" }} lượt tự đánh giá đã nhớ")
                Text("Từ cần học lại sẽ xuất hiện trong phiên tiếp theo khi đến hạn. Lịch ôn đã được lưu trên máy.")
                Button(onClick = { perform { repository.start(account.profile.id) } }, enabled = !busy) { Text("Kiểm tra phiên tiếp theo") }
                return@Column
            }
            Text("Từ ${current.session.position + 1}/${current.total}", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { current.session.position.toFloat() / current.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(current.word.word, style = MaterialTheme.typography.displaySmall)
                current.word.phonetic?.let { Text(it) }
                PronunciationButton(current.word.word)
                Text("Hãy nhớ nghĩa trước khi mở đáp án.")
                if (current.session.revealed) {
                    HorizontalDivider()
                    Text(current.word.translation, style = MaterialTheme.typography.headlineSmall)
                    Text(current.word.definition)
                    Text(current.word.exampleSentence)
                }
            } }
            if (!current.session.revealed) Button(onClick = { perform { repository.reveal(account.profile.id, current.session.id) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Hiện đáp án") }
            else {
                Text("Bạn nhớ từ này ở mức nào?")
                ReviewGrade.entries.forEach { grade ->
                    val next = current.card?.let { ReviewScheduler.next(it.schedule(), grade, System.currentTimeMillis()) }
                    val interval = when { next == null -> ""; next.intervalDays > 0 -> "${next.intervalDays} ngày"; grade == ReviewGrade.AGAIN -> "1 phút"; else -> "10 phút" }
                    OutlinedButton(onClick = { perform { repository.grade(account.profile.id, current.session.id, current.session.position, grade) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("${when (grade) { ReviewGrade.AGAIN -> "Quên"; ReviewGrade.HARD -> "Khó"; ReviewGrade.GOOD -> "Nhớ"; ReviewGrade.EASY -> "Dễ" }} • $interval")
                    }
                }
            }
            Text("Phiên tối đa 20 từ. Đóng màn hình để tiếp tục sau.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
