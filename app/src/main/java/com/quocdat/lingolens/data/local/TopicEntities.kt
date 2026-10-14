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
    @Upsert suspend fun saveTopic(topic:TopicCache)
    @Upsert suspend fun saveVocabulary(word:TopicVocabularyCache)
    @Query("SELECT * FROM TopicCache WHERE id=:id") suspend fun topic(id:String):TopicCache?
    @Query("SELECT * FROM TopicVocabularyCache WHERE topicId=:topicId ORDER BY displayOrder,word") suspend fun vocabularies(topicId:String):List<TopicVocabularyCache>
    @Query("DELETE FROM TopicCache WHERE id=:id") suspend fun deleteTopic(id:String)
}
