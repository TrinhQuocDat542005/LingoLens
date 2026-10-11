package com.quocdat.lingolens.sync

import com.quocdat.lingolens.review.core.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

data class CloudReviewCard(val word: String, val dueAt: Long, val intervalDays: Int, val ease: Double,
    val repetitions: Int, val lapses: Int, val stage: String, val lastReviewedAt: Long?)
data class CloudReviewEvent(val id: String, val word: String, val grade: String, val reviewedAt: Long)
data class ReviewSnapshot(val cards: List<CloudReviewCard>, val events: List<CloudReviewEvent>)

@Component
class ReviewStore(private val jdbc: JdbcTemplate) {
    fun initialize(account: Long, word: String, time: Long) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM review_cards WHERE user_id = ? AND word = ?", Int::class.java, account, word) == 0)
            jdbc.update("INSERT INTO review_cards(user_id, word, due_at) VALUES (?, ?, ?)", account, word, time)
    }
    fun snapshot(account: Long): ReviewSnapshot = ReviewSnapshot(
        jdbc.query("SELECT * FROM review_cards WHERE user_id = ? ORDER BY word", { r, _ ->
            CloudReviewCard(r.getString("word"), r.getLong("due_at"), r.getInt("interval_days"), r.getDouble("ease"),
                r.getInt("repetitions"), r.getInt("lapses"), r.getString("stage"), (r.getObject("last_reviewed_at") as? Number)?.toLong()) }, account),
        jdbc.query("SELECT * FROM review_events WHERE user_id = ? ORDER BY reviewed_at, operation_id", { r, _ ->
            CloudReviewEvent(r.getString("operation_id"), r.getString("word"), r.getString("grade"), r.getLong("reviewed_at")) }, account))
    fun review(account: Long, operationId: String, word: String, grade: ReviewGrade, time: Long) {
        require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_entries WHERE user_id = ? AND word = ?", Int::class.java, account, word) == 1) { "Save the word before reviewing" }
        initialize(account, word, time)
        val card = jdbc.queryForObject("SELECT * FROM review_cards WHERE user_id = ? AND word = ?", { r, _ ->
            Schedule(r.getLong("due_at"), r.getInt("interval_days"), r.getDouble("ease"), r.getInt("repetitions"),
                r.getInt("lapses"), r.getString("stage"), (r.getObject("last_reviewed_at") as? Number)?.toLong()) }, account, word)!!
        val next = ReviewScheduler.next(card, grade, time)
        jdbc.update("UPDATE review_cards SET due_at=?, interval_days=?, ease=?, repetitions=?, lapses=?, stage=?, last_reviewed_at=? WHERE user_id=? AND word=?",
            next.dueAt, next.intervalDays, next.ease, next.repetitions, next.lapses, next.stage, next.lastReviewedAt, account, word)
        jdbc.update("INSERT INTO review_events(user_id, operation_id, word, grade, reviewed_at) VALUES (?, ?, ?, ?, ?)", account, operationId, word, grade.name, time)
    }
}
