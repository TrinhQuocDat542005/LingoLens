package com.quocdat.lingolens.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.model.LearnedWord
import kotlinx.coroutines.flow.map
import java.util.Locale

fun normalizeWord(word: String) = word.trim().lowercase(Locale.ROOT)

class LearningRepository(private val db: LearningDatabase, private val gson: Gson = Gson(), private val onCommitted: (Long) -> Unit = {}) {
    private val dao = db.learningDao()
    fun words(accountId: Long) = dao.words(accountId).map { rows -> rows.map { gson.fromJson(it.content, LearnedWord::class.java).copy(timestamp = it.savedAt) } }
    fun events(accountId: Long) = dao.events(accountId)
    suspend fun find(accountId: Long, word: String): LearnedWord? = dao.find(accountId, normalizeWord(word))?.let { gson.fromJson(it.content, LearnedWord::class.java) }
    suspend fun save(accountId: Long, word: LearnedWord, now: Long = System.currentTimeMillis()) {
      db.withTransaction {
        val key = normalizeWord(word.word)
        val savedAt = dao.find(accountId, key)?.savedAt ?: now
        val snapshot = word.copy(id = key, word = key, timestamp = savedAt, imagePath = null, audioPath = null)
        dao.save(SavedWordEntity(accountId, key, gson.toJson(snapshot), savedAt))
        dao.record(LearningEvent(accountId, key, now))
        db.reviewDao().initialize(ReviewCard(accountId, key, now))
        db.syncDao().enqueue(OutboxOperation(operationId = java.util.UUID.randomUUID().toString(), accountId = accountId,
            kind = "SAVE_WORD", payload = gson.toJson(mapOf("word" to key, "content" to snapshot, "savedAt" to savedAt,
                "firstLearnedAt" to dao.event(accountId, key)!!.occurredAt))))
      }
      onCommitted(accountId)
    }
    suspend fun delete(accountId: Long, word: String) {
        val key = normalizeWord(word)
        db.withTransaction {
            dao.delete(accountId, key)
            db.syncDao().enqueue(OutboxOperation(operationId = java.util.UUID.randomUUID().toString(), accountId = accountId,
                kind = "DELETE_WORD", payload = gson.toJson(mapOf("word" to key))))
        }
        onCommitted(accountId)
    }
}
