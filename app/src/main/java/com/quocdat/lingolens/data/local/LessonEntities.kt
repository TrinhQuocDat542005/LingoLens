package com.quocdat.lingolens.data.local

import androidx.room.*

@Entity(primaryKeys = ["accountId", "revisionId"])
data class ContentPack(val accountId: Long, val revisionId: String, val lessonId: String, val courseId: String,
    val schemaVersion: Int, val manifestJson: String, val contentJson: String, val checksum: String,
    val byteLength: Long, val downloadedAt: Long, val complete: Boolean)

@Entity(primaryKeys = ["accountId", "id"],
    foreignKeys = [ForeignKey(entity = ContentPack::class, parentColumns = ["accountId", "revisionId"],
        childColumns = ["accountId", "revisionId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index(value = ["accountId", "revisionId"]), Index(value = ["accountId", "phase"])])
data class LessonSession(val accountId: Long, val id: String, val lessonId: String, val revisionId: String,
    val mode: String, val questionOrderJson: String, val position: Int, val answersJson: String,
    val phase: String, val startedAt: Long, val updatedAt: Long,
    val frozenPayloadJson: String? = null, val serverResultJson: String? = null)

@Entity(primaryKeys = ["accountId", "attemptId"],
    foreignKeys = [ForeignKey(entity = LessonSession::class, parentColumns = ["accountId", "id"],
        childColumns = ["accountId", "sessionId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index(value = ["accountId", "sessionId"], unique = true), Index(value = ["accountId", "state", "queuedAt"])])
data class LessonAttemptOutbox(val accountId: Long, val attemptId: String, val sessionId: String,
    val payloadJson: String, val payloadHash: String, val state: String, val queuedAt: Long,
    val retryCount: Int, val lastError: String? = null)

@Entity(primaryKeys = ["accountId", "courseId"])
data class CurriculumAccountState(val accountId: Long, val courseId: String, val enrollmentStatus: String,
    val confirmedProgressJson: String, val confirmedXpTotal: Int, val lastConfirmedAt: Long? = null)

@Dao
interface LessonDao {
    @Query("SELECT * FROM ContentPack WHERE accountId=:accountId")
    fun observePacks(accountId:Long):kotlinx.coroutines.flow.Flow<List<ContentPack>>
    @Query("SELECT * FROM LessonSession WHERE accountId=:accountId")
    fun observeSessions(accountId:Long):kotlinx.coroutines.flow.Flow<List<LessonSession>>
    @Query("SELECT * FROM LessonAttemptOutbox WHERE accountId=:accountId")
    fun observeOutbox(accountId:Long):kotlinx.coroutines.flow.Flow<List<LessonAttemptOutbox>>
    @Query("SELECT * FROM CurriculumAccountState WHERE accountId=:accountId")
    fun observeCurriculum(accountId:Long):kotlinx.coroutines.flow.Flow<List<CurriculumAccountState>>
    @Insert suspend fun insertPack(pack: ContentPack)
    @Query("SELECT * FROM ContentPack WHERE accountId=:accountId AND revisionId=:revisionId AND complete=1")
    suspend fun pack(accountId: Long, revisionId: String): ContentPack?
    @Query("SELECT * FROM ContentPack WHERE accountId=:accountId AND courseId=:courseId AND complete=1 ORDER BY downloadedAt DESC, revisionId")
    suspend fun completePacks(accountId: Long, courseId: String): List<ContentPack>
    @Upsert suspend fun saveSession(session: LessonSession)
    @Query("SELECT * FROM LessonSession WHERE accountId=:accountId AND id=:id")
    suspend fun session(accountId: Long, id: String): LessonSession?
    @Query("SELECT * FROM LessonSession WHERE accountId=:accountId ORDER BY updatedAt DESC, id")
    suspend fun sessions(accountId: Long): List<LessonSession>
    @Insert suspend fun enqueue(operation: LessonAttemptOutbox)
    @Query("SELECT * FROM LessonAttemptOutbox WHERE accountId=:accountId AND state!='CONFIRMED' ORDER BY queuedAt, attemptId")
    suspend fun pending(accountId: Long): List<LessonAttemptOutbox>
    @Query("SELECT * FROM LessonAttemptOutbox WHERE accountId=:accountId AND attemptId=:id")
    suspend fun outbox(accountId: Long,id:String):LessonAttemptOutbox?
    @Update suspend fun updateOutbox(operation:LessonAttemptOutbox)
    @Query("SELECT DISTINCT courseId FROM ContentPack WHERE accountId=:accountId AND complete=1")
    suspend fun courseIds(accountId:Long):List<String>
    @Query("UPDATE LessonAttemptOutbox SET state='CONFIRMED', lastError=NULL WHERE accountId=:accountId AND attemptId=:attemptId")
    suspend fun acknowledge(accountId: Long, attemptId: String)
    @Upsert suspend fun saveCurriculum(state: CurriculumAccountState)
    @Query("SELECT * FROM CurriculumAccountState WHERE accountId=:accountId AND courseId=:courseId")
    suspend fun curriculum(accountId: Long, courseId: String): CurriculumAccountState?
}
