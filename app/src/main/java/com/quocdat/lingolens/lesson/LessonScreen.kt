package com.quocdat.lingolens.lesson

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.quocdat.lingolens.ui.learning.LocalLearningSession
import kotlinx.coroutines.launch

@Composable
fun LessonScreen(navController:NavController) {
    val account=LocalLearningSession.current
    val context=LocalContext.current.applicationContext
    val model:OfflineLessonScreenModel=viewModel(key="lessons-${account.profile.id}",factory=OfflineLessonScreenModel.factory(
        account.container.lessonStudyRepository,account.profile.id,{
            val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)!=true
        }))
    val state by model.state.collectAsState()
    val scope=rememberCoroutineScope()
    LaunchedEffect(model){model.load()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick={if(state.stage=="CATALOG") navController.popBackStack() else model.backToCourses()}) {
            Text(if(state.stage=="CATALOG") "Về trang chủ" else "Về lộ trình")
        }
        Text(state.pack?.title ?: "Học theo lộ trình",style=MaterialTheme.typography.headlineSmall)
        if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        when(state.stage) {
            "CATALOG" -> {
                Text("XP lộ trình đã xác nhận: ${state.officialXp}",style=MaterialTheme.typography.titleMedium)
                Text("Tổng của các chặng đã tải; cập nhật khi có kết nối.")
                if(state.courses.isEmpty() && !state.busy) Text("Chưa có chặng đã tải. Kết nối mạng để tải lộ trình.")
                state.courses.forEach{course->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(course.title,style=MaterialTheme.typography.titleLarge)
                            if(course.demo) Text("DEMO • Nội dung thử nghiệm, chưa được rà soát độc lập")
                            Text(if(course.ready) "Đã tải đủ 3 bài • Có thể học offline" else "Đã tải ${course.downloaded}/3 bài • Cần tải đủ chặng trước khi học")
                            OutlinedButton(onClick={scope.launch{model.download(course.id)}},enabled=!state.busy) {
                                Text(if(course.ready) "Kiểm tra nội dung mới" else if(course.downloaded>0) "Tiếp tục tải chặng" else "Tải chặng")
                            }
                            course.lessons.forEach{lesson->
                                Text("Bài ${lesson.pack.position}: ${lesson.pack.title}",style=MaterialTheme.typography.titleMedium)
                                Text(when {
                                    lesson.confirmedPassed -> "Đã đạt • Server xác nhận"
                                    lesson.session?.phase=="BLOCKED" -> "Bị từ chối • Đáp án đã được giữ"
                                    lesson.session?.phase in setOf("QUEUED","WAITING_PREREQUISITE") -> "Chờ server xác nhận"
                                    lesson.session?.phase=="CONFIRMED" -> "Server xác nhận • Chưa đạt"
                                    lesson.session!=null -> "Đang học • Đã lưu trên máy"
                                    lesson.canOpen -> "Sẵn sàng"
                                    else -> "Cần vượt bài trước"
                                })
                                Button(onClick={scope.launch{model.openLesson(course.id,lesson.pack.lessonId)}},enabled=!state.busy && (lesson.canOpen || lesson.session!=null)) {
                                    Text(if(lesson.session==null) "Học bài ${lesson.pack.position}" else "Mở bài ${lesson.pack.position}")
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick={scope.launch{model.load()}},enabled=!state.busy){Text("Tải lại lộ trình")}
            }
            "LEARN" -> state.pack?.let{pack->
                if(pack.contentStatus=="DEMO") Text("DEMO • Nội dung thử nghiệm")
                Text(pack.introduction)
                Text("Từ trong bài",style=MaterialTheme.typography.titleLarge)
                pack.vocabularies.forEach{word->
                    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${word.word} • ${word.translation}",style=MaterialTheme.typography.titleMedium)
                        Text("${word.phonetic} · ${word.partOfSpeech}");Text(word.definition);Text(word.example)
                    }}
                }
                Text("Mẫu câu",style=MaterialTheme.typography.titleLarge)
                pack.sentencePatterns.forEach{Text("${it.pattern}\n${it.example}\n${it.translation}")}
                Button(onClick=model::showQuestions,enabled=!state.busy){Text("Bắt đầu trả lời")}
            }
            "QUESTIONS" -> state.pack?.questions?.getOrNull(state.questionIndex)?.let{question->
                Text("Câu ${state.questionIndex+1}/${state.questionCount}",style=MaterialTheme.typography.titleMedium)
                Text(question.prompt,style=MaterialTheme.typography.titleLarge)
                question.options.forEach{option->
                    OutlinedButton(onClick={scope.launch{model.choose(question.id,option.key)}},enabled=!state.frozen && !state.busy,modifier=Modifier.fillMaxWidth()) {
                        Text((if(state.answers[question.id]==option.key) "✓ " else "")+option.label)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={scope.launch{model.previous()}},enabled=state.questionIndex>0 && !state.busy){Text("Câu trước")}
                    Button(onClick={scope.launch{model.next()}},enabled=state.answers[question.id]!=null && !state.busy){Text("Tiếp tục")}
                }
            }
            "READY" -> {
                Text("Đã trả lời ${state.answers.size}/${state.questionCount} câu.")
                Text("Đạt bài khi điểm có trọng số từ 70%. Mỗi bài chỉ thưởng XP một lần.")
                Button(onClick={scope.launch{model.submit()}},enabled=!state.busy){Text("Nộp bài")}
                OutlinedButton(onClick={scope.launch{model.previous()}},enabled=!state.busy){Text("Xem lại đáp án")}
            }
            "SUMMARY" -> {
                Text("${state.correctCount}/${state.questionCount} câu đúng",style=MaterialTheme.typography.headlineMedium)
                when(state.session?.phase) {
                    "CONFIRMED" -> {
                        Text(if(state.passed==true) "Đã đạt • Server xác nhận" else "Chưa đạt • Server xác nhận")
                        state.confirmedResult?.let{Text("+${it.xpAwarded} XP đã xác nhận")}
                        Text("XP lộ trình đã xác nhận: ${state.officialXp}")
                        Button(onClick={scope.launch{model.restart()}},enabled=!state.busy){Text("Học lại bài này")}
                    }
                    "BLOCKED" -> {
                        Text("Server từ chối • Kết quả trên máy chưa được công nhận",color=MaterialTheme.colorScheme.error)
                        Text("Mã lỗi: ${state.syncError ?: "LESSON_REQUEST_INVALID"}. Đáp án được giữ; bài này không tự gửi lại.")
                    }
                    else -> {
                        Text(if(state.passed==true) "Đạt tạm • Chờ server xác nhận" else "Chưa đạt tạm • Chờ server xác nhận")
                        if(state.session?.phase=="WAITING_PREREQUISITE") Text("Server đang chờ xác nhận bài trước; sẽ thử lại cùng lượt nộp.")
                        Text("Chưa có XP chính thức cho lượt nộp này.")
                        OutlinedButton(onClick={scope.launch{model.retry()}},enabled=!state.busy){Text("Yêu cầu đồng bộ lại")}
                    }
                }
                val next=state.courses.firstOrNull{it.id==state.pack?.courseId}?.lessons?.firstOrNull{it.pack.position==(state.pack?.position ?: 0)+1}
                if(next!=null && next.canOpen) Button(onClick={scope.launch{model.openLesson(next.pack.courseId,next.pack.lessonId)}},enabled=!state.busy){Text("Học bài ${next.pack.position}")}
                Text("Đáp án đã lưu",style=MaterialTheme.typography.titleLarge)
                state.pack?.questions?.forEach{q->
                    Text(q.prompt)
                    Text("Bạn chọn: ${q.options.firstOrNull{it.key==state.answers[q.id]}?.label ?: "Chưa trả lời"}")
                }
            }
        }
    }
}
