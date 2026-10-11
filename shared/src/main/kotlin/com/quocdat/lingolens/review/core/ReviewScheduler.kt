package com.quocdat.lingolens.review.core

import kotlin.math.ceil

enum class ReviewGrade { AGAIN, HARD, GOOD, EASY }
data class Schedule(val dueAt: Long, val intervalDays: Int = 0, val ease: Double = 2.5,
    val repetitions: Int = 0, val lapses: Int = 0, val stage: String = "NEW", val lastReviewedAt: Long? = null)

/** Deterministic product heuristic, not an implementation of SM-2/FSRS or a measured memory model. */
object ReviewScheduler {
    const val DAY_MS = 86_400_000L
    fun next(previous: Schedule, grade: ReviewGrade, reviewedAt: Long): Schedule {
        require(reviewedAt >= 0 && reviewedAt <= Long.MAX_VALUE - 365 * DAY_MS)
        val base = maxOf(reviewedAt, previous.lastReviewedAt ?: reviewedAt)
        val learning = previous.stage != "REVIEW"
        val ease = when (grade) {
            ReviewGrade.AGAIN -> previous.ease - 0.2
            ReviewGrade.HARD -> previous.ease - 0.15
            ReviewGrade.GOOD -> previous.ease
            ReviewGrade.EASY -> previous.ease + 0.15
        }.coerceIn(1.3, 3.0)
        if (grade == ReviewGrade.AGAIN) return Schedule(base + 60_000, 0, ease, 0, previous.lapses + 1, "LEARNING", base)
        if (learning && grade == ReviewGrade.HARD) return Schedule(base + 600_000, 0, ease, previous.repetitions + 1, previous.lapses, "LEARNING", base)
        val days = when (grade) {
            ReviewGrade.HARD -> ceil(previous.intervalDays * 1.2).toInt()
            ReviewGrade.GOOD -> if (learning) 1 else ceil(previous.intervalDays * ease).toInt()
            ReviewGrade.EASY -> if (learning) 4 else ceil(previous.intervalDays * ease * 1.3).toInt()
            else -> error("Handled above")
        }.coerceIn(1, 365)
        return Schedule(base + days * DAY_MS, days, ease, previous.repetitions + 1, previous.lapses, "REVIEW", base)
    }
}
