package com.quocdat.lingolens.topic

import androidx.room.withTransaction
import com.google.gson.Gson
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import com.quocdat.lingolens.data.repository.normalizeWord
import com.quocdat.lingolens.model.LearnedWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import retrofit2.http.GET
import java.util.UUID

data class PublishedTopic(val id:String,val slug:String,val title:String,val version:Int,val words:List<String>)
interface TopicGateway {suspend fun published():List<PublishedTopic>}
interface TopicApi {@GET("api/v1/topics") suspend fun published():ApiEnvelope<List<PublishedTopic>>}
class RemoteTopicGateway(private val api:TopicApi):TopicGateway {
    override suspend fun published()=requireNotNull(api.published().takeIf{it.success}?.data)
}
class TopicRepository(private val db:LearningDatabase,private val gateway:TopicGateway,private val gson:Gson=Gson()) {
    fun topics()=db.topicDao().observeTopics()
    fun forWord(word:String)=db.topicDao().forWord(normalizeWord(word))
    fun words(account:Long,topic:String)=db.topicDao().words(account,topic).map{rows->rows.map{gson.fromJson(it.content,LearnedWord::class.java).copy(timestamp=it.savedAt)}}
    suspend fun refresh():Boolean = try {
        val topics=gateway.published()
        require(topics.size<=1 && topics.map{it.id}.distinct().size==topics.size)
        topics.forEach{topic->
            UUID.fromString(topic.id)
            require(topic.slug=="objects" && topic.title.isNotBlank() && topic.version>0)
            require(topic.words.distinct().size==topic.words.size && topic.words.all{it in setOf("book","bottle","phone")})
        }
        db.withTransaction {
            db.topicDao().clear()
            topics.forEach{topic->
                db.topicDao().saveTopic(TopicCache(topic.id,topic.slug,topic.title,topic.version,System.currentTimeMillis()))
                topic.words.forEachIndexed{index,word->db.topicDao().saveVocabulary(TopicVocabularyCache(topic.id,word,index))}
            }
        };true
    }catch(cancel:CancellationException){throw cancel}catch(_:Exception){false}
}
