package com.quocdat.lingolens.assessment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.quocdat.lingolens.auth.AuthService
import com.quocdat.lingolens.auth.RegisterRequest
import com.quocdat.lingolens.curriculum.ContentSeedImporter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest @org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test") @Testcontainers @Tag("postgres")
class LessonAttemptPostgresTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var service: LessonService
    @Autowired lateinit var auth: AuthService
    @Autowired lateinit var mockMvc: org.springframework.test.web.servlet.MockMvc
    @Autowired lateinit var users: com.quocdat.lingolens.user.UserRepository
    private var account = 0L
    private val mapper = jacksonObjectMapper()
    private val course = UUID.fromString("00000000-0000-4000-8000-000000000002")
    private val lesson = UUID.fromString("00000000-0000-4000-8000-000000000010")
    private val revision = UUID.fromString("00000000-0000-4000-8000-000000000020")
    @BeforeEach fun setup() {
        ContentSeedImporter(jdbc, mapper, TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)))
            .import(requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes(),setOf("test"))
        account = auth.register(RegisterRequest("lesson-${UUID.randomUUID()}@example.invalid","Secure123!","Learner")).id!!
        service.enroll(account,course)
    }
    private fun request(correct: Int = 8, mode: String = "LESSON", id: UUID = UUID.randomUUID()): SubmitLessonRequest {
        val questions = jdbc.queryForList("SELECT id,answer_key::text FROM questions WHERE lesson_revision_id=? ORDER BY position",revision)
        return SubmitLessonRequest(id,revision,mode,System.currentTimeMillis(),false,questions.mapIndexed { index,row ->
            val key = mapper.readTree(row["answer_key"].toString())["optionKey"].asText()
            SubmittedAnswer(row["id"] as UUID, if (index<correct) key else if (key=="A") "B" else "A")
        })
    }
    private fun count(table: String) = jdbc.queryForObject("SELECT count(*) FROM $table WHERE user_id=?",Int::class.java,account)!!
    @Test fun `first pass grants XP once and all retry results are stable`() {
        val input = request(6); val first = service.submit(account,input)
        assertTrue(first.passed); assertEquals(6,first.correctCount); assertEquals(8,first.questionCount); assertEquals(30,first.xpAwarded)
        assertEquals(first,service.submit(account,input.copy(answers=input.answers.reversed())))
        assertEquals(0,service.submit(account,request()).xpAwarded)
        assertEquals(first,service.result(account,input.id))
        assertEquals(2,count("lesson_attempts")); assertEquals(1,count("xp_events")); assertEquals(30,service.catalog(account).xpTotal)
    }
    @Test fun `changed UUID payload is conflict and other accounts cannot read or reuse results`() {
        val input=request(); service.submit(account,input)
        val changed=assertThrows(LessonRequestException::class.java) { service.submit(account,input.copy(mode="PRACTICE")) }
        assertEquals(409,changed.status.value())
        val other=auth.register(RegisterRequest("other-${UUID.randomUUID()}@example.invalid","Secure123!","Other")).id!!
        assertEquals(404,assertThrows(LessonRequestException::class.java) { service.result(other,input.id) }.status.value())
        assertEquals(404,assertThrows(LessonRequestException::class.java) { service.submit(other,input) }.status.value())
        assertEquals(1,count("lesson_attempts")); assertEquals(1,count("xp_events"))
    }
    @Test fun `practice and failed lesson do not grant XP or unlock`() {
        assertEquals(0,service.submit(account,request(mode="PRACTICE")).xpAwarded)
        assertFalse(service.submit(account,request(5)).passed)
        assertEquals(0,count("xp_events"))
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM lesson_progress WHERE user_id=? AND first_passed_at IS NOT NULL",Int::class.java,account)!!)
    }
    @Test fun `concurrent same UUID has one attempt and one award`() {
        val input=request(); val results=concurrent { service.submit(account,input) }
        assertEquals(results[0],results[1]); assertEquals(1,count("lesson_attempts")); assertEquals(1,count("xp_events"))
    }
    @Test fun `concurrent distinct UUIDs have two attempts and one award`() {
        val results=concurrent { service.submit(account,request()) }
        assertEquals(listOf(0,30),results.map { it.xpAwarded }.sorted())
        assertEquals(2,count("lesson_attempts")); assertEquals(1,count("xp_events"))
    }
    @Test fun `failure at XP write rolls back attempt answers progress and award`() {
        jdbc.execute("""CREATE FUNCTION fail_xp_write() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'Injected XP failure'; END; ${'$'}${'$'}""")
        jdbc.execute("CREATE TRIGGER fail_xp BEFORE INSERT ON xp_events FOR EACH ROW EXECUTE FUNCTION fail_xp_write()")
        val input=request()
        try {
            assertThrows(Exception::class.java) { service.submit(account,input) }
            assertEquals(0,count("lesson_attempts")); assertEquals(0,count("lesson_progress")); assertEquals(0,count("xp_events"))
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE attempt_id=?",Int::class.java,input.id)!!)
        } finally { jdbc.execute("DROP TRIGGER fail_xp ON xp_events"); jdbc.execute("DROP FUNCTION fail_xp_write()") }
        assertEquals(30,service.submit(account,input).xpAwarded)
    }
    @Test fun `lesson view uses published snapshots and does not expose answer keys`() {
        val view=service.lesson(account,lesson)
        assertEquals(8,view.questions.size); assertEquals(6,view.vocabularies.size)
        val json=mapper.writeValueAsString(view)
        assertFalse(json.contains("answerKey")); assertFalse(json.contains("correctKey"))
        assertEquals("DEMO",view.contentStatus)
    }
    @Test fun `authenticated HTTP retry and result ownership enforce the same contract`() {
        val owner=auth.login(com.quocdat.lingolens.auth.LoginRequest(users.findById(account).orElseThrow().getEmail(),"Secure123!"),null).accessToken
        val input=request()
        val first=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/lesson-attempts")
            .header("Authorization","Bearer $owner").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
        assertEquals(30,mapper.readTree(first)["data"]["xpAwarded"].asInt())
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/lesson-attempts")
            .header("Authorization","Bearer $owner").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input.copy(mode="PRACTICE"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict)
        val otherEmail="http-other-${UUID.randomUUID()}@example.invalid"
        auth.register(RegisterRequest(otherEmail,"Secure123!","Other"))
        val other=auth.login(com.quocdat.lingolens.auth.LoginRequest(otherEmail,"Secure123!"),null).accessToken
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/lesson-attempts/${input.id}").header("Authorization","Bearer $other"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)
        assertEquals(1,count("xp_events"))
    }
    private fun concurrent(action: () -> AttemptResult): List<AttemptResult> {
        val executor=Executors.newFixedThreadPool(2); val start=CountDownLatch(1)
        try { val jobs=(1..2).map { executor.submit<AttemptResult> { start.await(); action() } }; start.countDown(); return jobs.map { it.get(20,TimeUnit.SECONDS) } }
        finally { executor.shutdownNow() }
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        @DynamicPropertySource @JvmStatic fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url",postgres::getJdbcUrl); registry.add("spring.datasource.username",postgres::getUsername)
            registry.add("spring.datasource.password",postgres::getPassword); registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }
}
