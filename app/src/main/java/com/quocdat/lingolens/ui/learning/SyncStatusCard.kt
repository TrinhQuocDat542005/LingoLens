package com.quocdat.lingolens.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncStatusCard() {
    val session = LocalLearningSession.current
    val dao = session.container.syncDatabase.syncDao()
    val pending by remember(session.profile.id) { dao.observe(session.profile.id) }.collectAsState(initial = emptyList())
    val meta by remember(session.profile.id) { dao.observeAccount(session.profile.id) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (pending.isEmpty()) "Đồng bộ sổ tay" else "${pending.size} thao tác đang chờ đồng bộ", style = MaterialTheme.typography.titleSmall)
            Text(meta?.message ?: meta?.lastSyncedAt?.let { "Lần đồng bộ gần nhất: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" }
                ?: "Thay đổi được lưu trên thiết bị trước và tự gửi khi có mạng.", style = MaterialTheme.typography.bodySmall)
            if (pending.any { it.state == "BLOCKED" }) Text("Có thao tác bị từ chối. Hàng đợi đang dừng để giữ đúng thứ tự.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { scope.launch { dao.retry(session.profile.id); session.container.syncScheduler.schedule(session.profile.id) } }) { Text("Đồng bộ / thử lại") }
        }
    }
}
