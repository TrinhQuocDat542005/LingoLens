package com.quocdat.lingolens.lesson

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.quocdat.lingolens.ui.learning.LocalLearningSession
import kotlinx.coroutines.launch

@Composable
fun LessonScreen(navController: NavController) {
    val session=LocalLearningSession.current
    val model: LessonScreenModel=viewModel(factory=LessonScreenModel.factory(session.container.lessonRepository,session.profile.id))
    val state by model.state.collectAsState()
    val scope=rememberCoroutineScope()
    LaunchedEffect(model) { model.loadCatalog() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick={navController.popBackStack()}) { Text("Về trang chủ") }
        Text(state.lesson?.title ?: "Học theo lộ trình",style=MaterialTheme.typography.headlineSmall)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        when(state.stage) {
            "CATALOG" -> {
                Text("Tổng XP: ${state.catalog?.xpTotal ?: 0}",style=MaterialTheme.typography.titleMedium)
                if (state.catalog?.courses.isNullOrEmpty() && !state.busy) Text("Chưa có bài học được phát hành.")
                state.catalog?.courses?.forEach { course ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(course.title,style=MaterialTheme.typography.titleLarge)
                            if (course.contentStatus=="DEMO") Text("DEMO • Nội dung thử nghiệm, chưa được rà soát độc lập")
                            Text("Chặng đầu: 3 bài • Hiện có thể học bài đầu")
                            Button(onClick={scope.launch { model.open(course) }},enabled=!state.busy) { Text(if(course.completed) "Học lại bài đầu" else "Học bài đầu") }
                        }
                    }
                }
                OutlinedButton(onClick={scope.launch { model.loadCatalog() }},enabled=!state.busy) { Text("Tải lại lộ trình") }
            }
            "LEARN" -> state.lesson?.let { lesson ->
                if (lesson.contentStatus=="DEMO") Text("DEMO • Nội dung thử nghiệm")
                Text(lesson.introduction)
                Text("Từ trong bài",style=MaterialTheme.typography.titleLarge)
                lesson.vocabularies.forEach { word ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${word.word} • ${word.translation}",style=MaterialTheme.typography.titleMedium)
                        Text("${word.phonetic} · ${word.partOfSpeech}")
                        Text(word.definition); Text(word.example)
                    } }
                }
                Text("Mẫu câu",style=MaterialTheme.typography.titleLarge)
                lesson.sentencePatterns.forEach { pattern -> Text("${pattern.pattern}\n${pattern.example}\n${pattern.translation}") }
                Button(onClick=model::showQuestions) { Text("Bắt đầu trả lời") }
            }
            "QUESTIONS" -> state.lesson?.questions?.getOrNull(state.questionIndex)?.let { question ->
                Text("Câu ${state.questionIndex+1}/${state.lesson?.questions?.size}",style=MaterialTheme.typography.titleMedium)
                Text(question.prompt,style=MaterialTheme.typography.titleLarge)
                question.options.forEach { option ->
                    OutlinedButton(onClick={model.choose(question.id,option.key)},enabled=!state.frozen && !state.busy,modifier=Modifier.fillMaxWidth()) {
                        Text((if(state.answers[question.id]==option.key) "✓ " else "")+option.label)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick=model::previous,enabled=state.questionIndex>0 && !state.frozen) { Text("Câu trước") }
                    Button(onClick=model::next,enabled=state.answers[question.id]!=null) { Text("Tiếp tục") }
                }
            }
            "READY", "SUBMITTING" -> {
                Text("Đã trả lời ${state.answers.size}/${state.lesson?.questions?.size} câu.")
                Text("Đạt bài khi điểm có trọng số từ 70%. Mỗi bài chỉ thưởng XP một lần.")
                Button(onClick={scope.launch { model.submit() }},enabled=!state.busy) { Text(if(state.frozen) "Gửi lại để xác nhận" else "Nộp bài") }
            }
            "SUMMARY" -> state.result?.let { result ->
                Text("${result.correctCount}/${result.questionCount} câu đúng",style=MaterialTheme.typography.headlineMedium)
                Text(if(result.passed) "Đã đạt bài" else "Chưa đạt. Hãy xem lại từ và thử lần nữa.")
                Text("+${result.xpAwarded} XP",style=MaterialTheme.typography.titleLarge)
                Text("Tổng XP: ${state.catalog?.xpTotal ?: 0}")
                if (result.passed && result.xpAwarded==0) Text("Bài này đã nhận thưởng trước đó; học lại giúp củng cố kiến thức.")
                Button(onClick=model::restart) { Text("Học lại bài này") }
            }
        }
    }
}
