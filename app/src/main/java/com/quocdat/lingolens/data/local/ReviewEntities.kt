package com.quocdat.lingolens.data.local

import androidx.room.*
import com.quocdat.lingolens.review.core.Schedule
import kotlinx.coroutines.flow.Flow

@Entity(primaryKeys = ["accountId", "word"])
data class ReviewCard(val accountId: Long, val word: String, val dueAt: Long,
    val intervalDays: Int = 0, val ease: Double = 2.5, val repetitions: Int = 0,
    val lapses: Int = 0, val stage: String = "NEW", val lastReviewedAt: Long? = null) {
    fun schedule() = Schedule(dueAt, intervalDays, ease, repetitions, lapses, stage, lastReviewedAt)
    fun scheduled(next: Schedule) = copy(dueAt = next.dueAt, intervalDays = next.intervalDays, ease = next.ease,
        repetitions = next.repetitions, lapses = next.lapses, stage = next.stage, lastReviewedAt = next.lastReviewedAt)
}
@Entity(indices = [Index(value = ["accountId", "reviewedAt"])])
data class ReviewEvent(@PrimaryKey val id: String, val accountId: Long, val word: String,
    val grade: String, val reviewedAt: Long, val sessionId: String? = null)
@Entity(indices = [Index(value = ["accountId", "finishedAt"])])
data class ReviewSession(@PrimaryKey val id: String, val accountId: Long, val words: String,
    val position: Int = 0, val revealed: Boolean = false, val startedAt: Long, val finishedAt: Long? = null)

@Dao
interface ReviewDao {
    @Query("SELECT r.* FROM ReviewCard r INNER JOIN SavedWordEntity w ON w.accountId = r.accountId AND w.word = r.word WHERE r.accountId = :accountId ORDER BY r.dueAt, r.word")
    fun cards(accountId: Long): Flow<List<ReviewCard>>
    @Query("SELECT r.* FROM ReviewCard r INNER JOIN SavedWordEntity w ON w.accountId = r.accountId AND w.word = r.word WHERE r.accountId = :accountId AND r.dueAt <= :now ORDER BY r.dueAt, r.word LIMIT :limit")
    suspend fun due(accountId: Long, now: Long, limit: Int): List<ReviewCard>
    @Query("SELECT * FROM ReviewCard WHERE accountId = :accountId AND word = :word") suspend fun card(accountId: Long, word: String): ReviewCard?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun initialize(card: ReviewCard)
    @Upsert suspend fun save(card: ReviewCard)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun record(event: ReviewEvent)
    @Query("SELECT * FROM ReviewEvent WHERE accountId = :accountId ORDER BY reviewedAt DESC") fun events(accountId: Long): Flow<List<ReviewEvent>>
    @Query("SELECT * FROM ReviewEvent WHERE accountId = :accountId AND sessionId = :sessionId") suspend fun sessionEvents(accountId: Long, sessionId: String): List<ReviewEvent>
    @Query("SELECT * FROM ReviewSession WHERE accountId = :accountId AND finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1") suspend fun activeSession(accountId: Long): ReviewSession?
    @Query("SELECT * FROM ReviewSession WHERE accountId = :accountId AND id = :id") suspend fun session(accountId: Long, id: String): ReviewSession?
    @Upsert suspend fun save(session: ReviewSession)
    @Query("SELECT * FROM ReviewSession WHERE accountId=:accountId AND finishedAt IS NOT NULL AND finishedAt>=:boundary ORDER BY finishedAt,id")
    suspend fun finishedSessions(accountId:Long,boundary:Long):List<ReviewSession>
}
