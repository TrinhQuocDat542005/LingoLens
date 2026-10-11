package com.quocdat.lingolens.learning

import com.quocdat.lingolens.review.core.*
import org.junit.Test
import org.junit.Assert.*

class ReviewSchedulerTest {
    @Test fun `new grades use explicit learning steps`() {
        val initial = Schedule(100)
        assertEquals(60100L, ReviewScheduler.next(initial, ReviewGrade.AGAIN, 100).dueAt)
        assertEquals(600100L, ReviewScheduler.next(initial, ReviewGrade.HARD, 100).dueAt)
        assertEquals(1, ReviewScheduler.next(initial, ReviewGrade.GOOD, 100).intervalDays)
        assertEquals(4, ReviewScheduler.next(initial, ReviewGrade.EASY, 100).intervalDays)
    }
    @Test fun `long intervals ease limits lapse reset and backwards clock`() {
        var card = Schedule(0)
        repeat(100) { card = ReviewScheduler.next(card, ReviewGrade.EASY, card.dueAt) }
        assertEquals(365, card.intervalDays); assertEquals(3.0, card.ease, 0.001)
        val again = ReviewScheduler.next(card, ReviewGrade.AGAIN, 1)
        assertEquals(card.lastReviewedAt!! + 60000, again.dueAt)
        assertEquals(1, again.lapses); assertEquals(0, again.repetitions)
        repeat(100) { card = ReviewScheduler.next(card, ReviewGrade.HARD, card.dueAt) }
        assertEquals(1.3, card.ease, 0.001)
    }
}
