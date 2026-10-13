package com.quocdat.lingolens.lesson

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.LearningDatabase
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=android.app.Application::class)
class ContentPackRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val gson=Gson()
    private lateinit var db: LearningDatabase
    private lateinit var repository: ContentPackRepository
    private var signedIn=1L
    private val bytes= requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json")).bufferedReader().use { JsonParser.parseReader(it).asJsonArray }.map { gson.toJson(it).toByteArray() }
    private val packs=bytes.map { gson.fromJson(String(it),LessonPack::class.java) }
    private val course=packs.first().courseId
    private var wrongChecksum=false
    private var corruptStructure=false
    private var truncated=false
    private var wrongRevision=false
    private var wrongLocale:String?=null
    private var scheduled=0
    private var afterDownload: ()->Unit = {}
    private fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val gateway=object: PackGateway {
        override suspend fun manifest(courseId:String)=PackManifest(1,course,packs.mapIndexed { i,p ->
            val body=body(i)
            PackReference(p.lessonId,p.revisionId,p.title,p.position,p.contentStatus,"/api/v1/curriculum/revisions/${p.revisionId}/pack",body.size.toLong(),if(wrongChecksum && i==1) "bad" else hash(body))
        })
        private fun body(i:Int):ByteArray {
            wrongLocale?.let{locale->return String(bytes[i]).replace("\"en-US\"","\"$locale\"").toByteArray()}
            if(corruptStructure && i==0) return String(bytes[i]).replace("MEANING_CHOICE","UNKNOWN").toByteArray()
            if(wrongRevision && i==0) return String(bytes[i]).replace(packs[i].revisionId,"99999999-9999-4999-8999-999999999999").toByteArray()
            return bytes[i]
        }
        override suspend fun download(revisionId:String):ByteArray {
            val value=body(packs.indexOfFirst { it.revisionId==revisionId }); afterDownload(); return if(truncated) value.copyOf(value.size/2) else value
        }
    }
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(context,LearningDatabase::class.java).build()
        repository=ContentPackRepository(db,gateway,{signedIn==it},scheduleSync={scheduled++})
    }
    @After fun close(){ db.close() }
    @Test fun `large three lesson fixture validates complete format and account isolation`()=runBlocking {
        repository.downloadCourse(1,course)
        val ready=repository.ready(1,course); assertEquals(3,ready.size)
        ready.forEach { assertEquals(10,it.questions.size); assertEquals(6,it.vocabularies.size); assertTrue(it.questions.all { q -> q.options.all { o -> o.label.length>200 } }) }
        assertTrue(repository.ready(2,course).isEmpty())
        repository.downloadCourse(1,course); assertEquals(3,repository.ready(1,course).size)
    }
    @Test fun `bad checksum preserves good pack and retry finishes course`()=runBlocking {
        wrongChecksum=true
        var rejected=false; try{repository.downloadCourse(1,course)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected); assertNotNull(db.lessonDao().pack(1,packs[0].revisionId)); assertNull(db.lessonDao().pack(1,packs[1].revisionId))
        assertTrue(repository.ready(1,course).isEmpty())
        wrongChecksum=false; repository.downloadCourse(1,course); assertEquals(3,repository.ready(1,course).size)
    }
    @Test fun `invalid structure is not made complete even with matching checksum`()=runBlocking {
        corruptStructure=true
        var rejected=false; try{repository.downloadCourse(1,course)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected); assertNull(db.lessonDao().pack(1,packs[0].revisionId))
    }
    @Test fun `account change during download cannot persist the response`()=runBlocking {
        afterDownload={signedIn=2}
        var rejected=false; try{repository.downloadCourse(1,course)}catch(_:IllegalStateException){rejected=true}
        assertTrue(rejected); assertNull(db.lessonDao().pack(1,packs[0].revisionId)); assertTrue(repository.ready(2,course).isEmpty())
    }
    @Test fun `interrupted download never stores a complete pack`()=runBlocking {
        truncated=true
        var rejected=false; try{repository.downloadCourse(1,course)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected); assertNull(db.lessonDao().pack(1,packs[0].revisionId)); assertTrue(repository.ready(1,course).isEmpty())
        truncated=false; repository.downloadCourse(1,course); assertEquals(3,repository.ready(1,course).size)
    }
    @Test fun `wrong revision is rejected even with matching checksum`()=runBlocking {
        wrongRevision=true
        var rejected=false; try{repository.downloadCourse(1,course)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected); assertNull(db.lessonDao().pack(1,packs[0].revisionId)); assertTrue(repository.ready(1,course).isEmpty())
    }
    @Test fun `incomplete database pack cannot be read or used`()=runBlocking {
        val manifest=gateway.manifest(course); val p=packs[0]
        db.lessonDao().insertPack(com.quocdat.lingolens.data.local.ContentPack(1,p.revisionId,p.lessonId,course,1,gson.toJson(manifest),String(bytes[0]),hash(bytes[0]),bytes[0].size.toLong(),1,false))
        assertNull(db.lessonDao().pack(1,p.revisionId)); assertTrue(db.lessonDao().completePacks(1,course).isEmpty()); assertTrue(repository.ready(1,course).isEmpty())
    }
    @Test fun `changed manifest cannot overwrite a good immutable pack`()=runBlocking {
        repository.downloadCourse(1,course)
        val before=packs.map { db.lessonDao().pack(1,it.revisionId) }
        wrongChecksum=true
        var rejected=false; try{repository.downloadCourse(1,course)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected); assertEquals(before,packs.map { db.lessonDao().pack(1,it.revisionId) }); assertEquals(3,repository.ready(1,course).size)
    }
    @Test fun `complete download schedules confirmed state pull but partial download does not`()=runBlocking {
        wrongChecksum=true
        var rejected=false;try{repository.downloadCourse(1,course)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected);assertEquals(0,scheduled)
        wrongChecksum=false;repository.downloadCourse(1,course);assertEquals(1,scheduled)
    }
    @Test fun `non US pack locale is rejected before commit even with matching checksum`()=runBlocking {
        for(locale in listOf("en-GB","fr-FR")){
            wrongLocale=locale
            val rejected=try{repository.downloadCourse(1,course);null}catch(error:IllegalArgumentException){error}
            assertNotNull("Unsupported locale $locale must be rejected",rejected)
            assertEquals("Invalid content pack",rejected!!.message)
            packs.forEach{assertNull(db.lessonDao().pack(1,it.revisionId))}
            assertTrue(repository.ready(1,course).isEmpty());assertEquals(0,scheduled)
        }
        wrongLocale=null;repository.downloadCourse(1,course)
        val ready=repository.ready(1,course);assertEquals(3,ready.size)
        assertTrue(ready.all{it.pronunciationLocale=="en-US"})
    }
}
