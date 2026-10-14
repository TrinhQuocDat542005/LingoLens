package com.quocdat.lingolens.data.local

import androidx.room.*

// Public mapping cache; personal saved vocabulary remains in account-scoped SavedWordEntity.
@Entity(indices=[Index(value=["slug"],unique=true)])
data class TopicCache(@PrimaryKey val id:String,val slug:String,val title:String,val version:Int,val fetchedAt:Long)

@Entity(primaryKeys=["topicId","word"],
    foreignKeys=[ForeignKey(entity=TopicCache::class,parentColumns=["id"],childColumns=["topicId"],onDelete=ForeignKey.CASCADE)])
data class TopicVocabularyCache(val topicId:String,val word:String,val displayOrder:Int)

@Dao
interface TopicDao {
    @Query("SELECT * FROM TopicCache ORDER BY title,id") fun observeTopics():kotlinx.coroutines.flow.Flow<List<TopicCache>>
    @Query("SELECT t.* FROM TopicCache t JOIN TopicVocabularyCache v ON v.topicId=t.id WHERE v.word=:word ORDER BY t.title") fun forWord(word:String):kotlinx.coroutines.flow.Flow<List<TopicCache>>
    @Query("SELECT s.* FROM (SELECT * FROM SavedWordEntity WHERE accountId=:accountId) s JOIN TopicVocabularyCache v ON v.word=s.word WHERE v.topicId=:topicId ORDER BY s.savedAt DESC")
    fun words(accountId:Long,topicId:String):kotlinx.coroutines.flow.Flow<List<SavedWordEntity>>
    @Query("DELETE FROM TopicCache") suspend fun clear()
    @Upsert suspend fun saveTopic(topic:TopicCache)
    @Upsert suspend fun saveVocabulary(word:TopicVocabularyCache)
    @Query("SELECT * FROM TopicCache WHERE id=:id") suspend fun topic(id:String):TopicCache?
    @Query("SELECT * FROM TopicVocabularyCache WHERE topicId=:topicId ORDER BY displayOrder,word") suspend fun vocabularies(topicId:String):List<TopicVocabularyCache>
    @Query("DELETE FROM TopicCache WHERE id=:id") suspend fun deleteTopic(id:String)
}
