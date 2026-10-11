package com.quocdat.lingolens.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReviewOverviewCard(navController: NavController, statistics: Boolean = false) {
    val session = LocalLearningSession.current
    val repository = session.container.reviewRepository
    val cards by remember(session.profile.id) { repository.cards(session.profile.id) }.collectAsState(emptyList())
    val events by remember(session.profile.id) { repository.events(session.profile.id) }.collectAsState(emptyList())
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(30_000) } }
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ôn tập cách quãng", style = MaterialTheme.typography.titleLarge)
        Text("${cards.count { it.dueAt <= now }} từ đến hạn • ${cards.size} từ đang theo dõi")
        cards.filter { it.dueAt > now }.minByOrNull { it.dueAt }?.let {
            Text("Lịch tiếp theo: ${Instant.ofEpochMilli(it.dueAt).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))}")
        }
        Button(onClick = { navController.navigate("review") { launchSingleTop = true } }) { Text("Bắt đầu / tiếp tục ôn") }
        if (statistics) {
            val week = events.filter { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() in today.minusDays(6)..today }
            Text("Hôm nay: ${week.count { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() == today }} lượt ôn")
            Text("7 ngày: ${week.size} lượt • ${if (week.isEmpty()) 0 else week.count { it.grade != "AGAIN" } * 100 / week.size}% tự đánh giá đã nhớ")
            Text("Lịch hiện tại: ${cards.count { it.stage == "NEW" }} mới • ${cards.count { it.stage == "LEARNING" }} học lại • ${cards.count { it.stage == "REVIEW" }} ôn định kỳ")
            Text("Tỷ lệ nhớ dựa trên tự đánh giá, chưa phải phép đo năng lực.", style = MaterialTheme.typography.bodySmall)
        }
    } }
}
