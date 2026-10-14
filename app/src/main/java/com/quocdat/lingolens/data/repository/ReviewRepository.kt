package com.quocdat.lingolens.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.model.LearnedWord
import com.quocdat.lingolens.review.core.*
import java.util.UUID

data class ReviewProgress(val session: ReviewSession, val total: Int, val word: LearnedWord?, val card: ReviewCard?, val answers: List<ReviewEvent>)

class ReviewRepository(private val db: LearningDatabase, private val gson: Gson = Gson(), private val scheduleSync: (Long) -> Unit = {},
    private val onFinished:suspend(ReviewProgress)->Unit={}) {
    private val dao = db.reviewDao()
    fun cards(account: Long) = dao.cards(account)
    fun events(account: Long) = dao.events(account)
    private fun words(session: ReviewSession) = gson.fromJson(session.words, Array<String>::class.java).toList()
    suspend fun start(account: Long, resumeId: String? = null, now: Long = System.currentTimeMillis()): ReviewProgress {
        val result=db.withTransaction {
        val session = resumeId?.let { dao.session(account, it) } ?: dao.activeSession(account) ?: ReviewSession(
            UUID.randomUUID().toString(), account, gson.toJson(dao.due(account, now, 20).map { it.word }), startedAt = now).also { dao.save(it) }
        progress(session, now)
        }
        analytics(result);return result
    }
    private suspend fun progress(original: ReviewSession, now: Long): ReviewProgress {
        val keys = words(original)
        var session = original
        while (session.position < keys.size && db.learningDao().find(session.accountId, keys[session.position]) == null) {
            session = session.copy(position = session.position + 1, revealed = false)
        }
        if (session.position >= keys.size && session.finishedAt == null) session = session.copy(finishedAt = now)
        dao.save(session)
        val key = keys.getOrNull(session.position)
        val word = key?.let { db.learningDao().find(session.accountId, it) }?.let { gson.fromJson(it.content, LearnedWord::class.java) }
        return ReviewProgress(session, keys.size, word, key?.let { dao.card(session.accountId, it) }, dao.sessionEvents(session.accountId, session.id))
    }
    suspend fun reveal(account: Long, id: String): ReviewProgress {
        val result=db.withTransaction {
        val session = requireNotNull(dao.session(account, id))
        progress(session.copy(revealed = true), System.currentTimeMillis())
        }
        analytics(result);return result
    }
    suspend fun grade(account: Long, id: String, expectedPosition: Int, grade: ReviewGrade, now: Long = System.currentTimeMillis()): ReviewProgress {
        var committed = false
        val result = db.withTransaction {
            val current = progress(requireNotNull(dao.session(account, id)), now)
            if (current.session.position != expectedPosition || !current.session.revealed || current.word == null) return@withTransaction current
            val card = requireNotNull(current.card)
            val operationId = UUID.randomUUID().toString()
            dao.save(card.scheduled(ReviewScheduler.next(card.schedule(), grade, now)))
            dao.record(ReviewEvent(operationId, account, card.word, grade.name, now, id))
            db.syncDao().enqueue(OutboxOperation(operationId = operationId, accountId = account, kind = "REVIEW_WORD",
                payload = gson.toJson(mapOf("word" to card.word, "grade" to grade.name, "reviewedAt" to now))))
            committed = true
            progress(current.session.copy(position = expectedPosition + 1, revealed = false), now)
        }
        if (committed) scheduleSync(account)
        analytics(result)
        return result
    }
    private suspend fun analytics(progress:ReviewProgress){try{onFinished(progress)}catch(error:kotlinx.coroutines.CancellationException){throw error}catch(_:Exception){}}
}
