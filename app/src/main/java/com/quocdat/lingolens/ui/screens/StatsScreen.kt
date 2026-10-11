package com.quocdat.lingolens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.quocdat.lingolens.LingoLensApplication
import com.quocdat.lingolens.recognition.RecognitionHistoryState
import com.quocdat.lingolens.recognition.RecognitionHistoryViewModel
import androidx.compose.runtime.remember
import com.quocdat.lingolens.ui.learning.LocalLearningSession
import com.quocdat.lingolens.ui.learning.LearningStats
import com.quocdat.lingolens.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController) {
    val context = LocalContext.current
    val container = (context.applicationContext as LingoLensApplication).container
    val historyViewModel: RecognitionHistoryViewModel = viewModel(factory = RecognitionHistoryViewModel.Factory(container.recognitionRepository))
    val historyState by historyViewModel.state.collectAsState()
    val session = LocalLearningSession.current
    val repository = session.container.learningRepository
    val words by remember(session.profile.id) { repository.words(session.profile.id) }.collectAsState(initial = emptyList())
    val events by remember(session.profile.id) { repository.events(session.profile.id) }.collectAsState(initial = emptyList())
    val stats = LearningStats.calculate(events)
    val user = User(session.profile.name, session.profile.targetLevel, stats.streak, words.size, session.profile.dailyGoal)


    val weeklyData = stats.weekly
    val maxVal = weeklyData.maxOf { it.second }.coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thống Kê Tiến Độ", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // General Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Streak Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Streak hiện tại", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${user.streakDays} ngày 🔥", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Total Words Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Từ trong sổ tay", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${user.wordsLearnedCount} từ 📚", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            RecognitionHistoryCard(historyState, historyViewModel::refresh)

            // Beautiful Custom Bar Chart card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "7 ngày gần nhất",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Bar Chart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklyData.forEach { data ->
                            val barHeightRatio = data.second.toFloat() / maxVal
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (data.second > 0) "${data.second}" else "",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(barHeightRatio.coerceIn(0f, 1f))
                                        .width(18.dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary
                                                )
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = data.first,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Level Distribution Breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Phân phối cấp độ từ vựng",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    words.groupingBy { it.level }.eachCount().toSortedMap().forEach { (level, count) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(level)
                            Text("$count từ", fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { count.toFloat() / words.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }
                    if (words.isEmpty()) Text("Lưu từ đầu tiên để bắt đầu theo dõi tiến độ.")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RecognitionHistoryCard(state: RecognitionHistoryState, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Lịch sử nhận diện", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Dữ liệu đã đồng bộ với máy chủ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRefresh) { Text("Làm mới") }
            }
            when (state) {
                RecognitionHistoryState.Loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is RecognitionHistoryState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    OutlinedButton(onClick = onRefresh) { Text("Thử lại") }
                }
                is RecognitionHistoryState.Ready -> {
                    if (state.items.isEmpty()) Text("Chưa có lượt nhận diện nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.items.take(10).forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.detectedLabel.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                                Text("${item.engine ?: "AI"} · ${(item.confidence * 100).toInt()}% · ${item.createdAt.take(16).replace('T', ' ')}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.reported) AssistChip(onClick = {}, label = { Text("Đã báo cáo") })
                        }
                    }
                }
            }
        }
    }
}
