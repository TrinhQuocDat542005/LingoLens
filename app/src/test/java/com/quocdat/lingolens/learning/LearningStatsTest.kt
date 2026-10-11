package com.quocdat.lingolens.learning

import com.quocdat.lingolens.data.local.LearningEvent
import com.quocdat.lingolens.ui.learning.LearningStats
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LearningStatsTest {
    private val today = LocalDate.of(2026, 10, 11)
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun event(day: LocalDate, word: String = day.toString()) = LearningEvent(1, word, day.atStartOfDay(zone).toInstant().toEpochMilli())
    @Test fun `empty history has no invented activity`() {
        val stats = LearningStats.calculate(emptyList(), today, zone)
        assertEquals(0, stats.todayCount); assertEquals(0, stats.streak)
        assertEquals(7, stats.weekly.size); assertTrue(stats.weekly.all { it.second == 0 })
    }
    @Test fun `streak survives until end of day and stops on gaps`() {
        val stats = LearningStats.calculate(listOf(event(today.minusDays(1)), event(today.minusDays(2)), event(today.minusDays(4))), today, zone)
        assertEquals(2, stats.streak); assertEquals(0, stats.todayCount)
        assertEquals(3, stats.weekly.sumOf { it.second })
    }
    @Test fun `calendar boundaries use local timezone and ignore future dates for streak`() {
        val events = listOf(event(today), event(today, "second"), event(today.minusDays(1)), event(today.plusDays(1)))
        val stats = LearningStats.calculate(events, today, zone)
        assertEquals(2, stats.todayCount); assertEquals(2, stats.streak)
        assertEquals("11/10" to 2, stats.weekly.last())
    }
}
