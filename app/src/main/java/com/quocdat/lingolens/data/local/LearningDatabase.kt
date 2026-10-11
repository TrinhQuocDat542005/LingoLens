package com.quocdat.lingolens.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(primaryKeys = ["accountId", "word"])
data class SavedWordEntity(val accountId: Long, val word: String, val content: String, val savedAt: Long)
@Entity(primaryKeys = ["accountId", "word"])
data class LearningEvent(val accountId: Long, val word: String, val occurredAt: Long)
@Entity
data class VocabularyCache(@PrimaryKey val word: String, val content: String, val cachedAt: Long)

@Entity(indices = [Index(value = ["operationId"], unique = true), Index(value = ["accountId", "sequence"])])
data class OutboxOperation(@PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val operationId: String, val accountId: Long, val kind: String, val payload: String,
    val state: String = "PENDING", val attempts: Int = 0, val lastError: String? = null)
@Entity
data class SyncAccount(@PrimaryKey val accountId: Long, val bootstrapped: Boolean = false,
    val lastSyncedAt: Long? = null, val message: String? = null)

@Dao
interface LearningDao {
    @Query("SELECT * FROM SavedWordEntity WHERE accountId = :accountId") suspend fun allWords(accountId: Long): List<SavedWordEntity>
    @Query("SELECT * FROM LearningEvent WHERE accountId = :accountId") suspend fun allEvents(accountId: Long): List<LearningEvent>
    @Query("SELECT * FROM LearningEvent WHERE accountId = :accountId AND word = :word") suspend fun event(accountId: Long, word: String): LearningEvent?
    @Upsert suspend fun mergeEvent(event: LearningEvent)
    @Query("SELECT * FROM SavedWordEntity WHERE accountId = :accountId ORDER BY savedAt DESC")
    fun words(accountId: Long): Flow<List<SavedWordEntity>>
    @Query("SELECT * FROM LearningEvent WHERE accountId = :accountId")
    fun events(accountId: Long): Flow<List<LearningEvent>>
    @Query("SELECT * FROM SavedWordEntity WHERE accountId = :accountId AND word = :word")
    suspend fun find(accountId: Long, word: String): SavedWordEntity?
    @Upsert suspend fun save(word: SavedWordEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun record(event: LearningEvent)
    @Query("DELETE FROM SavedWordEntity WHERE accountId = :accountId AND word = :word")
    suspend fun delete(accountId: Long, word: String)
    @Query("SELECT * FROM VocabularyCache WHERE word = :word")
    suspend fun cached(word: String): VocabularyCache?
    @Upsert suspend fun cache(words: List<VocabularyCache>)
    @Query("DELETE FROM VocabularyCache") suspend fun clearCache()
    @Query("DELETE FROM VocabularyCache WHERE word = :word") suspend fun evict(word: String)
}

@Dao
interface SyncDao {
    @Insert suspend fun enqueue(operation: OutboxOperation): Long
    @Query("SELECT * FROM OutboxOperation WHERE accountId = :accountId ORDER BY sequence") suspend fun pending(accountId: Long): List<OutboxOperation>
    @Query("SELECT * FROM OutboxOperation WHERE accountId = :accountId ORDER BY sequence") fun observe(accountId: Long): Flow<List<OutboxOperation>>
    @Query("DELETE FROM OutboxOperation WHERE sequence = :sequence AND accountId = :accountId") suspend fun acknowledge(accountId: Long, sequence: Long)
    @Query("UPDATE OutboxOperation SET attempts = attempts + 1, lastError = :error, state = :state WHERE sequence = :sequence")
    suspend fun failure(sequence: Long, error: String, state: String)
    @Query("UPDATE OutboxOperation SET state = 'PENDING', lastError = NULL WHERE accountId = :accountId") suspend fun retry(accountId: Long)
    @Query("SELECT * FROM SyncAccount WHERE accountId = :accountId") suspend fun account(accountId: Long): SyncAccount?
    @Query("SELECT * FROM SyncAccount WHERE accountId = :accountId") fun observeAccount(accountId: Long): Flow<SyncAccount?>
    @Upsert suspend fun saveAccount(account: SyncAccount)
}

@Database(entities = [SavedWordEntity::class, LearningEvent::class, VocabularyCache::class, OutboxOperation::class, SyncAccount::class,
    ReviewCard::class, ReviewEvent::class, ReviewSession::class], version = 3, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao
    abstract fun syncDao(): SyncDao
    abstract fun reviewDao(): ReviewDao
    companion object {
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ReviewCard (accountId INTEGER NOT NULL, word TEXT NOT NULL, dueAt INTEGER NOT NULL, intervalDays INTEGER NOT NULL, ease REAL NOT NULL, repetitions INTEGER NOT NULL, lapses INTEGER NOT NULL, stage TEXT NOT NULL, lastReviewedAt INTEGER, PRIMARY KEY(accountId, word))")
                db.execSQL("CREATE TABLE IF NOT EXISTS ReviewEvent (id TEXT PRIMARY KEY NOT NULL, accountId INTEGER NOT NULL, word TEXT NOT NULL, grade TEXT NOT NULL, reviewedAt INTEGER NOT NULL, sessionId TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ReviewEvent_accountId_reviewedAt ON ReviewEvent(accountId, reviewedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS ReviewSession (id TEXT PRIMARY KEY NOT NULL, accountId INTEGER NOT NULL, words TEXT NOT NULL, position INTEGER NOT NULL, revealed INTEGER NOT NULL, startedAt INTEGER NOT NULL, finishedAt INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ReviewSession_accountId_finishedAt ON ReviewSession(accountId, finishedAt)")
                db.execSQL("INSERT INTO ReviewCard SELECT accountId, word, savedAt, 0, 2.5, 0, 0, 'NEW', NULL FROM SavedWordEntity")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS OutboxOperation (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, operationId TEXT NOT NULL, accountId INTEGER NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, lastError TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_OutboxOperation_operationId ON OutboxOperation(operationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_OutboxOperation_accountId_sequence ON OutboxOperation(accountId, sequence)")
                db.execSQL("CREATE TABLE IF NOT EXISTS SyncAccount (accountId INTEGER PRIMARY KEY NOT NULL, bootstrapped INTEGER NOT NULL, lastSyncedAt INTEGER, message TEXT)")
            }
        }
    }
}
