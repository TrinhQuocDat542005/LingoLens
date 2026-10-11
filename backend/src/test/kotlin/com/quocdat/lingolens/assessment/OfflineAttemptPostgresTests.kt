package com.quocdat.lingolens.assessment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.quocdat.lingolens.auth.*
import com.quocdat.lingolens.curriculum.ContentSeedImporter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.test.context.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.*
import java.util.UUID
import java.util.concurrent.*

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @Tag("postgres")
class OfflineAttemptPostgresTests {
    @Autowired lateinit var jdbc:JdbcTemplate
    @Autowired lateinit var service:LessonService
    @Autowired lateinit var auth:AuthService
    @Autowired lateinit var mockMvc:MockMvc
    private val mapper=jacksonObjectMapper()
    private val course=UUID.fromString("00000000-0000-4000-8000-000000000002")
    private lateinit var revisions:List<UUID>
    private var account=0L
    private lateinit var token:String
    @BeforeEach fun setup(){
        ContentSeedImporter(jdbc,mapper,TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)))
            .import(requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes(),setOf("test"))
        val email="offline-${UUID.randomUUID()}@example.invalid"
        account=auth.register(RegisterRequest(email,"Secure123!","Offline")).id!!
        token=auth.login(LoginRequest(email,"Secure123!"),null).accessToken
        service.enroll(account,course)
        revisions=jdbc.queryForList("SELECT r.id FROM lesson_revisions r JOIN lessons l ON l.id=r.lesson_id WHERE r.status='PUBLISHED' ORDER BY l.position",UUID::class.java)
    }
    private fun request(index:Int,id:UUID=UUID.randomUUID())=SubmitLessonRequest(id,revisions[index],"LESSON",System.currentTimeMillis()-30L*86_400_000,true,
        jdbc.queryForList("SELECT id,answer_key::text FROM questions WHERE lesson_revision_id=? ORDER BY position",revisions[index]).map {
            SubmittedAnswer(it["id"] as UUID,mapper.readTree(it["answer_key"].toString())["optionKey"].asText())
        })
    private fun count(table:String)=jdbc.queryForObject("SELECT count(*) FROM $table WHERE user_id=?",Int::class.java,account)!!
    private fun concurrent(requests:List<SubmitLessonRequest>):List<AttemptResult>{
        val pool=Executors.newFixedThreadPool(2); val start=CountDownLatch(1)
        try{val jobs=requests.map { input -> pool.submit<AttemptResult>{start.await();service.submit(account,input)} };start.countDown();return jobs.map{it.get(20,TimeUnit.SECONDS)}}
        finally{pool.shutdownNow()}
    }
    @Test fun `out of order valid attempts persist then confirm only after prerequisites`() {
        val third=request(2); val second=request(1)
        for(input in listOf(third,second)){
            val pending=service.submit(account,input)
            assertEquals("WAITING_PREREQUISITE",pending.confirmationStatus);assertTrue(pending.retryable);assertEquals("LESSON_PREREQUISITE_PENDING",pending.code)
            assertEquals(0,pending.xpAwarded);assertEquals(pending,service.submit(account,input))
            assertEquals(8,jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE attempt_id=?",Int::class.java,input.id)!!)
        }
        assertEquals(2,count("lesson_attempts"));assertEquals(0,count("lesson_progress"));assertEquals(0,count("xp_events"))
        assertEquals(30,service.submit(account,request(0)).xpAwarded)
        assertEquals("WAITING_PREREQUISITE",service.submit(account,third).confirmationStatus)
        val confirmed=service.submit(account,second);assertEquals("CONFIRMED",confirmed.confirmationStatus);assertFalse(confirmed.retryable);assertEquals(30,confirmed.xpAwarded)
        assertEquals(confirmed,service.submit(account,second));assertEquals(30,service.submit(account,third).xpAwarded)
        assertEquals(3,count("lesson_attempts"));assertEquals(3,count("xp_events"))
        assertTrue(jdbc.queryForObject("SELECT submitted_offline FROM lesson_attempts WHERE id=?",Boolean::class.java,second.id)!!)
    }
    @Test fun `concurrent pending retries and different UUIDs award only once`() {
        val input=request(1);service.submit(account,input);service.submit(account,request(0))
        val same=concurrent(listOf(input,input));assertEquals(same[0],same[1]);assertEquals(2,count("xp_events"))
        val thirdA=request(2);val thirdB=request(2)
        val distinct=concurrent(listOf(thirdA,thirdB));assertEquals(listOf(0,30),distinct.map{it.xpAwarded}.sorted())
        assertEquals(4,count("lesson_attempts"));assertEquals(3,count("xp_events"))
    }
    @Test fun `pending confirmation rollback retains original grade and waiting state`() {
        val input=request(1);val pending=service.submit(account,input);service.submit(account,request(0))
        val answers=jdbc.queryForList("SELECT * FROM attempt_answers WHERE attempt_id=? ORDER BY question_id",input.id)
        jdbc.execute("""CREATE FUNCTION fail_offline_xp() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'Injected failure'; END; ${'$'}${'$'}""")
        jdbc.execute("CREATE TRIGGER fail_offline_xp BEFORE INSERT ON xp_events FOR EACH ROW EXECUTE FUNCTION fail_offline_xp()")
        try{assertThrows(Exception::class.java){service.submit(account,input)};assertEquals(pending,service.result(account,input.id));assertEquals(1,count("lesson_progress"));assertEquals(1,count("xp_events"));assertEquals(answers,jdbc.queryForList("SELECT * FROM attempt_answers WHERE attempt_id=? ORDER BY question_id",input.id))}
        finally{jdbc.execute("DROP TRIGGER fail_offline_xp ON xp_events");jdbc.execute("DROP FUNCTION fail_offline_xp()")}
        assertEquals(30,service.submit(account,input).xpAwarded)
    }
    @Test fun `HTTP pending errors and ownership have stable codes`() {
        val input=request(1)
        mockMvc.perform(post("/api/v1/lesson-attempts").header("Authorization","Bearer $token").contentType("application/json").content(mapper.writeValueAsString(input)))
            .andExpect(status().isAccepted).andExpect(jsonPath("$.code").value("LESSON_PREREQUISITE_PENDING")).andExpect(jsonPath("$.data.retryable").value(true))
        mockMvc.perform(post("/api/v1/lesson-attempts").header("Authorization","Bearer $token").contentType("application/json").content(mapper.writeValueAsString(input.copy(submittedOffline=false))))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("ATTEMPT_PAYLOAD_CONFLICT"))
        val invalid=request(0).copy(answers=input.answers)
        val error=assertThrows(LessonRequestException::class.java){service.submit(account,invalid)};assertEquals("LESSON_REQUEST_INVALID",error.code)
        val missing=assertThrows(LessonRequestException::class.java){service.submit(account,request(0).copy(lessonRevisionId=UUID.randomUUID()))};assertEquals("LESSON_REVISION_UNAVAILABLE",missing.code)
        val other=auth.register(RegisterRequest("other-${UUID.randomUUID()}@example.invalid","Secure123!","Other")).id!!
        assertEquals(404,assertThrows(LessonRequestException::class.java){service.result(other,input.id)}.status.value())
        assertEquals(1,count("lesson_attempts"));assertEquals(0,count("lesson_progress"))
    }
    @Test fun `archived revision remains gradable without changing snapshot`() {
        val input=request(0);jdbc.update("UPDATE lesson_revisions SET status='ARCHIVED' WHERE id=?",input.lessonRevisionId)
        try{assertEquals(30,service.submit(account,input).xpAwarded);assertEquals(0,service.submit(account,request(0)).xpAwarded)}
        finally{jdbc.update("UPDATE lesson_revisions SET status='PUBLISHED' WHERE id=?",input.lessonRevisionId)}
    }
    @Test @org.springframework.security.test.context.support.WithMockUser(username="admin@example.invalid",roles=["ADMIN"])
    fun `admin deleting referenced vocabulary returns conflict and preserves rows`() {
        val id=jdbc.queryForObject("SELECT vocabulary_id FROM lesson_vocabularies ORDER BY vocabulary_id LIMIT 1",Long::class.java)!!
        val before=jdbc.queryForList("SELECT * FROM lesson_vocabularies WHERE vocabulary_id=?",id)
        mockMvc.perform(delete("/api/v1/admin/vocabularies/$id")).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("VOCABULARY_IN_USE"))
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM vocabularies WHERE id=?",Int::class.java,id)!!);assertEquals(before,jdbc.queryForList("SELECT * FROM lesson_vocabularies WHERE vocabulary_id=?",id))
    }
    @Test fun `course snapshot contains only server confirmed progress and course XP`() {
        service.submit(account,request(1))
        mockMvc.perform(get("/api/v1/curriculum/courses/$course/state").header("Authorization","Bearer $token").header("X-LingoLens-Account",account))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.xpTotal").value(0)).andExpect(jsonPath("$.data.progress.length()").value(0))
        service.submit(account,request(0))
        mockMvc.perform(get("/api/v1/curriculum/courses/$course/state").header("Authorization","Bearer $token").header("X-LingoLens-Account",account))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.xpTotal").value(30)).andExpect(jsonPath("$.data.progress[0].passed").value(true))
    }
    @Test fun `account header mismatch rejects submit and snapshot before data access`() {
        val input=request(0)
        mockMvc.perform(post("/api/v1/lesson-attempts").header("Authorization","Bearer $token").header("X-LingoLens-Account",account+1).contentType("application/json").content(mapper.writeValueAsString(input)))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("ACCOUNT_MISMATCH"))
        mockMvc.perform(get("/api/v1/curriculum/courses/$course/state").header("Authorization","Bearer $token").header("X-LingoLens-Account",account+1))
            .andExpect(status().isForbidden)
        assertEquals(0,count("lesson_attempts"));assertEquals(0,count("xp_events"))
        mockMvc.perform(get("/api/v1/curriculum/courses/$course/state")).andExpect(status().isUnauthorized)
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        @DynamicPropertySource @JvmStatic fun database(registry:DynamicPropertyRegistry){
            registry.add("spring.datasource.url",postgres::getJdbcUrl);registry.add("spring.datasource.username",postgres::getUsername);registry.add("spring.datasource.password",postgres::getPassword)
            registry.add("spring.datasource.driver-class-name"){"org.postgresql.Driver"}
        }
    }
}
