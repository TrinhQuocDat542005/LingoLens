package com.quocdat.lingolens.lesson

import androidx.room.withTransaction
import com.google.gson.Gson
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.ContentPack
import com.quocdat.lingolens.data.local.LearningDatabase
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import java.security.MessageDigest
import java.util.UUID

interface PackApi {
    @GET("api/v1/curriculum/courses/{id}/manifest") suspend fun manifest(@Path("id") id:String):Response<ApiEnvelope<PackManifest>>
    @GET("api/v1/curriculum/revisions/{id}/pack") suspend fun download(@Path("id") id:String):Response<ResponseBody>
}
interface PackGateway {
    suspend fun manifest(courseId:String):PackManifest
    suspend fun download(revisionId:String):ByteArray
}
class RemotePackGateway(private val api:PackApi):PackGateway {
    override suspend fun manifest(courseId:String):PackManifest {
        val response=api.manifest(courseId)
        if(!response.isSuccessful) throw retrofit2.HttpException(response)
        return requireNotNull(response.body()?.takeIf { it.success }?.data)
    }
    override suspend fun download(revisionId:String):ByteArray {
        val response=api.download(revisionId)
        if(!response.isSuccessful) throw retrofit2.HttpException(response)
        return requireNotNull(response.body()).use { body ->
            require(body.contentLength()<=2_000_000)
            body.byteStream().use { input ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(true){ val n=input.read(buffer); if(n<0) break; require(output.size()+n<=2_000_000); output.write(buffer,0,n) }
                output.toByteArray()
            }
        }
    }
}
class ContentPackRepository(private val db:LearningDatabase,private val gateway:PackGateway,
    private val isSignedIn:suspend(Long)->Boolean,private val gson:Gson=Gson(),private val scheduleSync:(Long)->Unit={}) {
    suspend fun downloadCourse(account:Long,course:String) {
        check(account>0 && isSignedIn(account))
        val manifest=gateway.manifest(course)
        require(manifest.schemaVersion==1 && manifest.courseId==course && manifest.lessons.size==3)
        require(manifest.lessons.map { it.revisionId }.distinct().size==3 && manifest.lessons.map { it.lessonId }.distinct().size==3)
        require(manifest.lessons.map { it.position }==listOf(1,2,3))
        val manifestJson=gson.toJson(manifest)
        for(reference in manifest.lessons) {
            check(isSignedIn(account))
            val cached=db.lessonDao().pack(account,reference.revisionId)
            if(cached!=null){ require(cached.checksum==reference.checksum && cached.byteLength==reference.byteLength); continue }
            require(reference.byteLength in 1..2_000_000)
            val bytes=gateway.download(reference.revisionId)
            check(isSignedIn(account))
            require(bytes.size.toLong()==reference.byteLength && hash(bytes)==reference.checksum) { "Pack checksum or length mismatch" }
            val content=decode(bytes); val pack=parse(content)
            require(pack.courseId==course && pack.lessonId==reference.lessonId && pack.revisionId==reference.revisionId && pack.position==reference.position && pack.contentStatus==reference.contentStatus)
            db.withTransaction {
                check(isSignedIn(account))
                if(db.lessonDao().pack(account,pack.revisionId)==null) db.lessonDao().insertPack(ContentPack(account,pack.revisionId,pack.lessonId,course,1,manifestJson,content,reference.checksum,bytes.size.toLong(),System.currentTimeMillis(),true))
            }
        }
        check(isSignedIn(account));scheduleSync(account)
    }
    suspend fun ready(account:Long,course:String):List<LessonPack> {
        val saved=db.lessonDao().completePacks(account,course)
        if(saved.isEmpty()) return emptyList()
        val manifest=gson.fromJson(saved.first().manifestJson,PackManifest::class.java)
        val selected=manifest.lessons.map { ref -> saved.firstOrNull { it.revisionId==ref.revisionId && it.checksum==ref.checksum } ?: return emptyList() }
        return selected.map { parse(it.contentJson) }
    }
    fun parse(content:String):LessonPack = try {
        val pack=requireNotNull(gson.fromJson(content,LessonPack::class.java))
        require(pack.schemaVersion==1 && pack.pronunciationLocale=="en-US" && pack.contentStatus in setOf("DEMO","REVIEWED"))
        listOf(pack.courseId,pack.lessonId,pack.revisionId).forEach { UUID.fromString(it) }
        require(pack.title.isNotBlank() && pack.introduction.isNotBlank() && pack.position in 1..3)
        require(pack.vocabularies.size in 5..24 && pack.vocabularies.map { it.word }.distinct().size==pack.vocabularies.size)
        require(pack.vocabularies.all { it.word.isNotBlank() && it.translation.isNotBlank() && it.definition.isNotBlank() && it.level in setOf("A1","B1","B2") })
        require(pack.sentencePatterns.isNotEmpty() && pack.sentencePatterns.all { it.pattern.isNotBlank() && it.example.isNotBlank() && it.translation.isNotBlank() })
        require(pack.questions.size in 8..10 && pack.questions.map { it.id }.distinct().size==pack.questions.size)
        require(pack.questions.map { it.position }==(1..pack.questions.size).toList())
        pack.questions.forEach { q ->
            UUID.fromString(q.id)
            require(q.type=="MEANING_CHOICE" && q.prompt.isNotBlank() && q.explanation.isNotBlank() && q.weight in 1..10)
            require(q.options.size==4 && q.options.map { it.key }.distinct().size==4 && q.options.map { it.position }==listOf(1,2,3,4))
            require(q.options.all { it.key.isNotBlank() && it.label.isNotBlank() } && q.answerKey.optionKey in q.options.map { it.key })
        }
        pack
    } catch(error:Exception){ throw IllegalArgumentException("Invalid content pack",error) }
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun decode(bytes:ByteArray)=Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
}
