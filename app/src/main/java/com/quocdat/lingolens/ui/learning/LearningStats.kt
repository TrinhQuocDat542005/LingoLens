package com.quocdat.lingolens.ui.learning

import com.quocdat.lingolens.data.local.LearningEvent
import java.time.*

data class LearningStats(val todayCount: Int, val streak: Int, val weekly: List<Pair<String, Int>>) {
    companion object {
        fun calculate(events: List<LearningEvent>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): LearningStats {
            val counts = events.groupingBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }.eachCount()
            var cursor = if ((counts[today] ?: 0) > 0) today else today.minusDays(1)
            var streak = 0
            while ((counts[cursor] ?: 0) > 0) { streak++; cursor = cursor.minusDays(1) }
            return LearningStats(counts[today] ?: 0, streak, (6L downTo 0L).map { days ->
                val day = today.minusDays(days)
                "${day.dayOfMonth}/${day.monthValue}" to (counts[day] ?: 0)
            })
        }
    }
}
