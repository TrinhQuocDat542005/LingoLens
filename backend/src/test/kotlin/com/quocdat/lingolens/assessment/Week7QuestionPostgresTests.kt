package com.quocdat.lingolens.assessment

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.quocdat.lingolens.auth.*
import com.quocdat.lingolens.curriculum.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.test.context.*
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.*
import java.util.UUID
import java.util.concurrent.*

/** PostgreSQL-only. All four datasource properties match the existing postgresTest mechanism. */
@SpringBootTest @ActiveProfiles("test") @Testcontainers @Tag("postgres")
class Week7QuestionPostgresTests {
    @Autowired lateinit var jdbc:JdbcTemplate
    @Autowired lateinit var service:LessonService
    @Autowired lateinit var auth:AuthService
    @Autowired lateinit var packs:ContentPackService
    @Autowired lateinit var codec:PackWireCodec
    private val mapper=jacksonObjectMapper()
    private val course=UUID.fromString("00000000-0000-4000-8000-000000000002")
    private val oldRevision=UUID.fromString("00000000-0000-4000-8000-000000000020")
    private val oldBytes get()=requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes()
    private val newBytes get()=requireNotNull(javaClass.getResource("/content/demo-week7.json")).readBytes()
    private fun importer()=ContentSeedImporter(jdbc,mapper,TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)))
    private fun freshAccount():Long {
        val account=auth.register(RegisterRequest("mixed-${UUID.randomUUID()}@example.invalid","Secure123!","Mixed")).id!!
        service.enroll(account,course);return account
    }
    private fun request(revision:UUID,correct:Int=10,id:UUID=UUID.randomUUID()):SubmitLessonRequest {
        val answers=jdbc.queryForList("SELECT id,type,answer_key::text FROM questions WHERE lesson_revision_id=? ORDER BY position",revision).mapIndexed { index,q ->
            val key=mapper.readTree(q["answer_key"].toString())
            val value=if(q["type"]=="FILL_GAP") {
                if(index<correct) "  "+key["acceptedAnswers"][0].asText().uppercase(java.util.Locale.ROOT)+"  " else "definitely wrong"
            } else {
                if(index<correct) key["optionKey"].asText() else jdbc.queryForList("SELECT option_key FROM question_options WHERE question_id=? AND option_key<>? ORDER BY position",String::class.java,q["id"],key["optionKey"].asText()).first()
            }
            SubmittedAnswer(q["id"] as UUID,value)
        }
        return SubmitLessonRequest(id,revision,"LESSON",System.currentTimeMillis(),true,answers)
    }
    private fun newRevision()=mapper.readTree(newBytes)["lessons"][0]["revision"]["id"].asText().let(UUID::fromString)
    @BeforeEach fun databaseIsRealAndSeed() {
        jdbc.dataSource!!.connection.use { assertEquals("PostgreSQL",it.metaData.databaseProductName);assertTrue(it.metaData.url.startsWith("jdbc:postgresql:")) }
        // Spring caches this context between methods; each test gets a distinct existing course/pack pair.
        // Content schema fixtures are immutable, so no broad database delete/reset is used.
        importer().import(oldBytes,setOf("test"))
    }
    @Test fun `mixed revision seed preserves legacy rows bytes receipt and old grading`() {
        val account=freshAccount();val old=request(oldRevision,8);service.submit(account,old)
        val before=rowsForAccount(account)
        val oldPack=packs.pack(oldRevision);val oldQuestions=jdbc.queryForList("SELECT * FROM questions WHERE lesson_revision_id=? ORDER BY id",oldRevision)
        val oldReceipt=jdbc.queryForList("SELECT * FROM content_import_receipts WHERE pack_id=?",UUID.fromString("00000000-0000-4000-8000-000000000001"))
        importer().import(newBytes,setOf("test"))
        assertEquals(before,rowsForAccount(account));assertArrayEquals(oldPack,packs.pack(oldRevision))
        assertEquals(oldQuestions,jdbc.queryForList("SELECT * FROM questions WHERE lesson_revision_id=? ORDER BY id",oldRevision));assertEquals(oldReceipt,jdbc.queryForList("SELECT * FROM content_import_receipts WHERE pack_id=?",UUID.fromString("00000000-0000-4000-8000-000000000001")))
        assertEquals("ARCHIVED",jdbc.queryForObject("SELECT status FROM lesson_revisions WHERE id=?",String::class.java,oldRevision))
        assertEquals(0,service.submit(account,request(oldRevision,8)).xpAwarded)
        assertEquals(3,packs.manifest(course).lessons.size)
        packs.manifest(course).lessons.forEach { ref ->
            val body=mapper.readTree(packs.pack(UUID.fromString(ref.revisionId)))
            assertEquals(10,body["questions"].size());assertEquals(2,body["questions"].count{it["type"].asText()=="LISTEN_CHOICE"})
            body["questions"].filter{it["type"].asText()=="LISTEN_CHOICE"}.forEach{assertTrue(it["speechText"].asText().isNotBlank());assertTrue(it["fallbackPrompt"].asText().isNotBlank())}
            body["questions"].filter{it["type"].asText()=="FILL_GAP"}.forEach{assertEquals("",it["answerKey"]["optionKey"].asText());assertTrue(it["answerKey"]["acceptedAnswers"].size()>0)}
        }
    }
    @Test fun `mixed raw answers grade seven pass six fail and normalized retry stays raw conflict`() {
        importer().import(newBytes,setOf("test"));val revision=newRevision()
        val passAccount=freshAccount();val passed=request(revision,7);val pass=service.submit(passAccount,passed)
        assertEquals(7,pass.correctCount);assertEquals(10,pass.questionCount);assertEquals(70,pass.scorePercent);assertTrue(pass.passed);assertEquals(30,pass.xpAwarded)
        assertEquals(pass,service.submit(passAccount,passed))
        val fill=passed.answers.indexOfFirst { answer ->jdbc.queryForObject("SELECT type FROM questions WHERE id=?",String::class.java,answer.questionId)=="FILL_GAP" }
        val changed=passed.copy(answers=passed.answers.mapIndexed { index,a -> if(index==fill)a.copy(optionKey=a.optionKey.trim().lowercase(java.util.Locale.ROOT)) else a })
        val conflict=assertThrows(LessonRequestException::class.java){service.submit(passAccount,changed)};assertEquals("ATTEMPT_PAYLOAD_CONFLICT",conflict.code)
        val failAccount=freshAccount();val fail=service.submit(failAccount,request(revision,6))
        assertEquals(6,fail.correctCount);assertEquals(60,fail.scorePercent);assertFalse(fail.passed);assertEquals(0,fail.xpAwarded)
    }
    @Test fun `blank fill and invalid choice reject without attempt progress or XP`() {
        importer().import(newBytes,setOf("test"));val account=freshAccount();val input=request(newRevision())
        val fill=input.answers.indexOfFirst{jdbc.queryForObject("SELECT type FROM questions WHERE id=?",String::class.java,it.questionId)=="FILL_GAP"}
        for((index,value) in listOf(fill to " \t ",fill to "x".repeat(501),0 to "NOT_AN_OPTION")) {
            val bad=input.copy(id=UUID.randomUUID(),answers=input.answers.mapIndexed{i,a->if(i==index)a.copy(optionKey=value) else a})
            val error=assertThrows(LessonRequestException::class.java){service.submit(account,bad)};assertEquals("LESSON_REQUEST_INVALID",error.code)
            assertTrue(rowsForAccount(account).getValue("lesson_attempts").isEmpty());assertTrue(rowsForAccount(account).getValue("lesson_progress").isEmpty());assertTrue(rowsForAccount(account).getValue("xp_events").isEmpty())
        }
    }
    @Test fun `large mixed format fixture roundtrips typed metadata and long choice labels`() {
        val fixture=mapper.readTree(requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs-week7.json")))
        assertEquals(3,fixture.size())
        fixture.forEach { body ->
            val pack=mapper.treeToValue(body,com.quocdat.lingolens.assessment.core.LessonPack::class.java)
            assertEquals(body,mapper.readTree(codec.encode(pack)));assertEquals(10,pack.questions.size);assertEquals(6,pack.vocabularies.size)
            assertEquals(setOf("MEANING_CHOICE","LISTEN_CHOICE","FILL_GAP"),pack.questions.map{it.type}.toSet())
            assertTrue(pack.questions.filter{it.type!="FILL_GAP"}.all{q->q.options.all{it.label.length>200}})
        }
    }
    @Test fun `metadata mismatch rolls back new receipt and concurrent upgrade imports once`() {
        val bad=mapper.readTree(newBytes) as ObjectNode;bad.put("packId",UUID.randomUUID().toString());(bad["course"] as ObjectNode).put("title","unexpected metadata")
        val receipts=jdbc.queryForList("SELECT * FROM content_import_receipts ORDER BY pack_id")
        assertThrows(IllegalArgumentException::class.java){importer().import(mapper.writeValueAsBytes(bad),setOf("test"))}
        assertEquals(receipts,jdbc.queryForList("SELECT * FROM content_import_receipts ORDER BY pack_id"))
        // Separate stable course/topic/unit/lesson IDs avoid touching canonical revision history.
        // This test can run before or after any canonical week7 import without ordering requirements.
        val identity=StableIdentity();val root=cloneSeed(newBytes,identity)
        val bytes=mapper.writeValueAsBytes(root);val pool=Executors.newFixedThreadPool(2);val start=CountDownLatch(1)
        try {val jobs=(1..2).map{pool.submit<Boolean>{start.await();importer().import(bytes,setOf("test"))}};start.countDown();assertEquals(listOf(false,true),jobs.map{it.get(30,TimeUnit.SECONDS)}.sorted())}
        finally{pool.shutdownNow()}
        assertEquals(3,packs.manifest(identity.course).lessons.size)
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM lesson_revisions r JOIN lessons l ON l.id=r.lesson_id JOIN course_units u ON u.id=l.unit_id WHERE r.status='PUBLISHED' AND u.course_id=?",Int::class.java,identity.course)!!)
    }
    @Test fun `fresh mixed course refuses older seed and retains receipt manifest and pack bytes`() {
        val identity=StableIdentity();val mixed=cloneSeed(newBytes,identity);val legacy=cloneSeed(oldBytes,identity)
        val mixedBytes=mapper.writeValueAsBytes(mixed);val legacyBytes=mapper.writeValueAsBytes(legacy)
        assertTrue(importer().import(mixedBytes,setOf("test")));assertFalse(importer().import(mixedBytes,setOf("test")))
        val before=packs.manifest(identity.course);val bytesBefore=before.lessons.associate{it.revisionId to packs.pack(UUID.fromString(it.revisionId))}
        val receipts=jdbc.queryForList("SELECT * FROM content_import_receipts ORDER BY pack_id")
        val error=assertThrows(IllegalArgumentException::class.java){importer().import(legacyBytes,setOf("test"))}
        assertTrue(error.message.orEmpty().contains("Revision number must increase"))
        assertEquals(before,packs.manifest(identity.course));assertEquals(receipts,jdbc.queryForList("SELECT * FROM content_import_receipts ORDER BY pack_id"))
        before.lessons.forEach{assertArrayEquals(bytesBefore.getValue(it.revisionId),packs.pack(UUID.fromString(it.revisionId)))}
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM lesson_revisions WHERE pack_id=?",Int::class.java,UUID.fromString(legacy["packId"].asText()))!!)
    }
    private data class StableIdentity(val course:UUID=UUID.randomUUID(),val topic:UUID=UUID.randomUUID(),val unit:UUID=UUID.randomUUID(),
        val lessons:List<UUID> = List(3){UUID.randomUUID()},val suffix:String=UUID.randomUUID().toString())
    private fun cloneSeed(bytes:ByteArray,identity:StableIdentity):ObjectNode {
        val root=mapper.readTree(bytes) as ObjectNode;root.put("packId",UUID.randomUUID().toString())
        (root["course"] as ObjectNode).put("id",identity.course.toString()).put("code","isolated-${identity.suffix}")
        (root["topic"] as ObjectNode).put("id",identity.topic.toString()).put("slug","isolated-${identity.suffix}")
        (root["unit"] as ObjectNode).put("id",identity.unit.toString()).put("courseId",identity.course.toString()).put("topicId",identity.topic.toString())
        root["lessons"].forEachIndexed { index,node ->
            (node as ObjectNode).put("id",identity.lessons[index].toString()).put("unitId",identity.unit.toString())
            (node["revision"] as ObjectNode).put("id",UUID.randomUUID().toString())
            node["questions"].forEach{(it as ObjectNode).put("id",UUID.randomUUID().toString())}
        }
        return root
    }
    private fun rowsForAccount(account:Long)=listOf("lesson_attempts","lesson_progress","xp_events","enrollments").associateWith { table ->
        jdbc.queryForList("SELECT row_to_json(t)::text FROM $table t WHERE user_id=? ORDER BY row_to_json(t)::text",String::class.java,account)
    } + mapOf("attempt_answers" to jdbc.queryForList("SELECT row_to_json(t)::text FROM attempt_answers t JOIN lesson_attempts a ON a.id=t.attempt_id WHERE a.user_id=? ORDER BY row_to_json(t)::text",String::class.java,account))
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        @DynamicPropertySource @JvmStatic fun database(registry:DynamicPropertyRegistry) {
            registry.add("spring.datasource.url",postgres::getJdbcUrl);registry.add("spring.datasource.username",postgres::getUsername);registry.add("spring.datasource.password",postgres::getPassword)
            registry.add("spring.datasource.driver-class-name"){"org.postgresql.Driver"}
        }
    }
}
