package com.quocdat.lingolens.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(primaryKeys = ["accountId", "word"])
data class SavedWordEntity(val accountId: Long, val word: String, val content: String, val savedAt: Long)
@Entity(primaryKeys = ["accountId", "word"])
data class LearningEvent(val accountId: Long, val word: String, val occurredAt: Long)
@Entity
data class VocabularyCache(@PrimaryKey val word: String, val content: String, val cachedAt: Long)

@Dao
interface LearningDao {
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

@Database(entities = [SavedWordEntity::class, LearningEvent::class, VocabularyCache::class], version = 1, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() { abstract fun learningDao(): LearningDao }
