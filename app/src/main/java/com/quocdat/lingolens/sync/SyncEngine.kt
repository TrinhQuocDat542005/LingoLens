package com.quocdat.lingolens.sync

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.repository.normalizeWord
import com.quocdat.lingolens.model.LearnedWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

enum class SyncOutcome { COMPLETE, RETRY, PAUSED, BLOCKED }

class SyncEngine(private val db: LearningDatabase, private val api: SyncApi,
    private val isSignedIn: suspend (Long) -> Boolean, private val gson: Gson = Gson()) {
    private val mutex = Mutex()
    suspend fun awaitIdle(){mutex.withLock { Unit }}
    suspend fun bootstrap(accountId: Long) = db.withTransaction {
        if (db.syncDao().account(accountId)?.bootstrapped == true) return@withTransaction
        db.learningDao().allEvents(accountId).forEach { event ->
            enqueue(accountId, "RECORD_LEARNING", mapOf("word" to event.word, "firstLearnedAt" to event.occurredAt))
        }
        db.learningDao().allWords(accountId).forEach { word ->
            enqueue(accountId, "SAVE_WORD", mapOf("word" to word.word, "content" to gson.fromJson(word.content, JsonObject::class.java),
                "savedAt" to word.savedAt, "firstLearnedAt" to db.learningDao().event(accountId, word.word)?.occurredAt))
        }
        db.syncDao().saveAccount(SyncAccount(accountId, bootstrapped = true))
    }
    private suspend fun enqueue(accountId: Long, kind: String, payload: Any) = db.syncDao().enqueue(
        OutboxOperation(operationId = UUID.randomUUID().toString(), accountId = accountId, kind = kind, payload = gson.toJson(payload)))

    suspend fun run(accountId: Long): SyncOutcome = mutex.withLock {
        if (!isSignedIn(accountId)) return@withLock SyncOutcome.PAUSED
        bootstrap(accountId)
        var current: OutboxOperation? = null
        try {
            // Bound each worker invocation; another invocation continues long queues.
            for (iteration in 0 until 100) {
                if (!isSignedIn(accountId)) return@withLock SyncOutcome.PAUSED
                current = db.syncDao().pending(accountId).firstOrNull()
                val operation = current ?: break
                if (operation.state == "BLOCKED") return@withLock SyncOutcome.BLOCKED
                val receipt = requireNotNull(api.apply(accountId, SyncRequest(accountId, operation.operationId, operation.kind,
                    gson.fromJson(operation.payload, JsonObject::class.java))).data)
                require(receipt.operationId == operation.operationId) { "Unexpected operation acknowledgement" }
                // An ambiguous response/crash leaves the same operation ID for the next attempt.
                db.syncDao().acknowledge(accountId, operation.sequence)
                current = null
            }
            if (db.syncDao().pending(accountId).isNotEmpty()) return@withLock SyncOutcome.RETRY
            if (!isSignedIn(accountId)) return@withLock SyncOutcome.PAUSED
            val snapshot = requireNotNull(api.snapshot(accountId, accountId).data)
            val reviews = requireNotNull(api.reviews(accountId, accountId).data)
            if (!isSignedIn(accountId)) return@withLock SyncOutcome.PAUSED
            db.withTransaction {
                // Local edits made while GET was in flight must win until acknowledged.
                val protected = db.syncDao().pending(accountId).mapNotNull {
                    gson.fromJson(it.payload, JsonObject::class.java).get("word")?.asString?.let(::normalizeWord)
                }.toSet()
                snapshot.filterNot { it.word in protected }.forEach { entry ->
                    if (entry.deleted) db.learningDao().delete(accountId, entry.word)
                    else if (entry.content != null && entry.savedAt != null) {
                        val word = gson.fromJson(entry.content, LearnedWord::class.java)
                            .copy(id = entry.word, word = entry.word, timestamp = entry.savedAt, imagePath = null, audioPath = null)
                        db.learningDao().save(SavedWordEntity(accountId, entry.word, gson.toJson(word), entry.savedAt))
                        db.reviewDao().initialize(ReviewCard(accountId, entry.word, entry.savedAt))
                    }
                    entry.firstLearnedAt?.let { time ->
                        val existing = db.learningDao().event(accountId, entry.word)
                        db.learningDao().mergeEvent(LearningEvent(accountId, entry.word, minOf(existing?.occurredAt ?: time, time)))
                    }
                }
                reviews.cards.filterNot { it.word in protected }.forEach {
                    db.reviewDao().save(ReviewCard(accountId, it.word, it.dueAt, it.intervalDays, it.ease, it.repetitions, it.lapses, it.stage, it.lastReviewedAt))
                }
                reviews.events.forEach { db.reviewDao().record(ReviewEvent(it.id, accountId, it.word, it.grade, it.reviewedAt)) }
                val meta = db.syncDao().account(accountId) ?: SyncAccount(accountId, true)
                db.syncDao().saveAccount(meta.copy(lastSyncedAt = System.currentTimeMillis(), message = null))
            }
            SyncOutcome.COMPLETE
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            if (!isSignedIn(accountId)) return@withLock SyncOutcome.PAUSED
            val auth = ex is HttpException && ex.code() in listOf(401, 403)
            val transient = ex is IOException || (ex is HttpException && (ex.code() >= 500 || ex.code() in listOf(408, 429)))
            val message = when { auth -> "Cần đăng nhập lại để đồng bộ."
                transient -> "Chưa kết nối được máy chủ. Hệ thống sẽ tự thử lại."
                else -> "Máy chủ từ chối thao tác. Kiểm tra dữ liệu rồi thử lại." }
            current?.let { db.syncDao().failure(it.sequence, message, if (auth || transient) "PENDING" else "BLOCKED") }
            val meta = db.syncDao().account(accountId) ?: SyncAccount(accountId, true)
            db.syncDao().saveAccount(meta.copy(message = message))
            when { auth -> SyncOutcome.PAUSED; transient -> SyncOutcome.RETRY; else -> SyncOutcome.BLOCKED }
        }
    }
}
