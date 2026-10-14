package com.quocdat.lingolens.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.auth.*
import com.quocdat.lingolens.assessment.*
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
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID
import java.util.concurrent.*

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @Tag("postgres")
@TestMethodOrder(MethodOrderer.Random::class)
class AnalyticsPostgresTests {
    @Autowired lateinit var jdbc:JdbcTemplate
    @Autowired lateinit var mapper:ObjectMapper
    @Autowired lateinit var auth:AuthService
    @Autowired lateinit var analytics:AnalyticsService
    @Autowired lateinit var lessons:LessonService
    @Autowired lateinit var mvc:MockMvc
    @Autowired lateinit var context:org.springframework.context.ApplicationContext
    private val createdAccounts=mutableListOf<Long>()
    private var account=0L
    private lateinit var token:String
    @BeforeEach fun setup(){
        jdbc.dataSource!!.connection.use{assertEquals("PostgreSQL",it.metaData.databaseProductName);assertTrue(it.metaData.url.startsWith("jdbc:postgresql:"))}
        val email="analytics-${UUID.randomUUID()}@example.invalid"
        account=register(email,"Analytics");token=auth.login(LoginRequest(email,"Secure123!"),null).accessToken
    }
    private fun register(email:String,name:String):Long=auth.register(RegisterRequest(email,"Secure123!",name)).id!!.also{createdAccounts.add(it)}
    @AfterEach fun cleanup(){
        TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)).executeWithoutResult{
            for(id in createdAccounts){
                jdbc.update("DELETE FROM analytics_events WHERE user_id=?",id)
                jdbc.update("DELETE FROM lesson_progress WHERE user_id=?",id)
                jdbc.update("DELETE FROM attempt_answers WHERE attempt_id IN (SELECT id FROM lesson_attempts WHERE user_id=?)",id)
                jdbc.update("DELETE FROM xp_events WHERE user_id=?",id)
                jdbc.update("DELETE FROM lesson_attempts WHERE user_id=?",id)
                jdbc.update("DELETE FROM enrollments WHERE user_id=?",id)
                jdbc.update("DELETE FROM learner_settings WHERE user_id=?",id)
                jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?",id)
                jdbc.update("DELETE FROM user_roles WHERE user_id=?",id)
                jdbc.update("DELETE FROM users WHERE id=?",id)
                assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Int::class.java,id))
                assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE user_id=?",Int::class.java,id))
            }
        }
    }
    @Test fun `automatic retention jobs are disabled in PostgreSQL tests`() {
        assertEquals("false",context.environment.getProperty("app.analytics.retention.enabled"))
        assertTrue(context.getBeansOfType(AnalyticsRetentionLifecycle::class.java).isEmpty())
        assertEquals(1,context.getBeansOfType(AnalyticsLifecycle::class.java).size)
    }
    private fun event(name:String="app_open",id:UUID=UUID.randomUUID(),source:UUID=UUID.randomUUID(),revision:Long=1,at:Instant=Instant.now())=
        mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(mapOf("id" to id.toString(),"eventName" to name,
            (if(name.startsWith("lesson"))"attemptId" else "sessionId") to source.toString(),"occurredAt" to at.toString(),
            "appVersion" to "test","timezone" to "UTC","properties" to emptyMap<String,String>(),"consentRevision" to revision))
    private fun optIn()=analytics.updateConsent(account,ConsentRequest(true,"analytics-v1",0))
    private fun count()=jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE user_id=?",Int::class.java,account)!!
    @Test fun `authentication account fence and legacy profile consent isolation`() {
        mvc.perform(get("/api/v1/analytics/consent")).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/v1/analytics/consent").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/analytics/events/batch").contentType("application/json").content("{\"events\":[]}" )).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/analytics/consent").header("Authorization","Bearer $token")).andExpect(status().isOk).andExpect(jsonPath("$.data.optIn").value(false)).andExpect(jsonPath("$.data.revision").value(0))
        mvc.perform(post("/api/v1/analytics/events/batch").header("Authorization","Bearer $token").header("X-LingoLens-Account",account+1)
            .contentType("application/json").content(mapper.writeValueAsString(BatchRequest(listOf(event()))))).andExpect(status().isForbidden)
        mvc.perform(put("/api/v1/analytics/consent").header("Authorization","Bearer $token").header("X-LingoLens-Account",account+1)
            .contentType("application/json").content("""{"optIn":true,"consentVersion":"analytics-v1","expectedRevision":0}""")).andExpect(status().isForbidden)
        mvc.perform(put("/api/v1/analytics/consent").header("Authorization","Bearer $token").contentType("application/json")
            .content("""{"optIn":true,"consentVersion":"analytics-v1","expectedRevision":0}""")).andExpect(status().isOk).andExpect(jsonPath("$.data.revision").value(1))
        mvc.perform(put("/api/v1/users/me").header("Authorization","Bearer $token").contentType("application/json").content("""{"name":"Updated","targetLevel":"B2","dailyGoal":17}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.targetLevel").value("B2"))
        assertEquals(1L,analytics.consent(account).revision);assertTrue(analytics.consent(account).optIn)
        mvc.perform(get("/api/v1/admin/analytics/retention").header("Authorization","Bearer $token")).andExpect(status().isForbidden)
    }
    @Test fun `consent CAS defaults opt out stale generation and no backfill`() {
        assertEquals("DROPPED_OPT_OUT",analytics.batch(account,BatchRequest(listOf(event()))).results.single().status)
        assertThrows(IllegalArgumentException::class.java){analytics.updateConsent(account,ConsentRequest(true,"unknown",0))}
        val enabled=optIn();assertEquals(enabled,analytics.updateConsent(account,ConsentRequest(true,"analytics-v1",1)))
        val stale=assertThrows(AnalyticsRequestException::class.java){analytics.updateConsent(account,ConsentRequest(false,null,0))};assertEquals("CONSENT_REVISION_CONFLICT",stale.code)
        assertEquals("ACCEPTED",analytics.batch(account,BatchRequest(listOf(event()))).results.single().status)
        assertFalse(analytics.updateConsent(account,ConsentRequest(false,null,1)).optIn)
        assertEquals("DROPPED_OPT_OUT",analytics.batch(account,BatchRequest(listOf(event()))).results.single().status);assertEquals(1,count())
        assertEquals(3L,analytics.updateConsent(account,ConsentRequest(true,"analytics-v1",2)).revision)
        assertEquals("DROPPED_STALE_CONSENT",analytics.batch(account,BatchRequest(listOf(event()))).results.single().status);assertEquals(1,count())
    }
    @Test fun `batch partial failure UUID and source dedupe payload conflict and ownership`() {
        optIn();val good=event();val bad=event().put("email","private")
        assertEquals(listOf("ACCEPTED","REJECTED_INVALID"),analytics.batch(account,BatchRequest(listOf(good,bad))).results.map{it.status})
        assertEquals("DUPLICATE",analytics.batch(account,BatchRequest(listOf(good))).results.single().status)
        assertEquals("DUPLICATE",analytics.batch(account,BatchRequest(listOf(good.deepCopy().put("id",UUID.randomUUID().toString())))).results.single().status)
        assertEquals("REJECTED_UUID_CONFLICT",analytics.batch(account,BatchRequest(listOf(good.deepCopy().put("appVersion","changed")))).results.single().status)
        val other=register("other-${UUID.randomUUID()}@example.invalid","Other")
        analytics.updateConsent(other,ConsentRequest(true,"analytics-v1",0))
        assertEquals("REJECTED_UUID_CONFLICT",analytics.batch(other,BatchRequest(listOf(good))).results.single().status)
        assertEquals(1,count());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE user_id=?",Int::class.java,other))
        assertThrows(IllegalArgumentException::class.java){analytics.batch(account,BatchRequest((1..51).map{event()}))}
        val huge=event();huge.putObject("properties").put("answer","x".repeat(140000))
        assertThrows(IllegalArgumentException::class.java){analytics.batch(account,BatchRequest(listOf(huge)))}
        mvc.perform(post("/api/v1/analytics/events/batch").header("Authorization","Bearer $token").contentType("application/json")
            .content(mapper.writeValueAsString(BatchRequest(listOf(event("lesson_completed")))))).andExpect(status().isOk).andExpect(jsonPath("$.data.results[0].status").value("ACCEPTED"))
    }
    @Test fun `concurrent batch duplicate and opt out serialize on PostgreSQL`() {
        optIn();val input=BatchRequest(listOf(event()));val pool=Executors.newFixedThreadPool(2);val start=CountDownLatch(1)
        try{
            val jobs=(1..2).map{pool.submit<String>{start.await();analytics.batch(account,input).results.single().status}};start.countDown()
            assertEquals(listOf("ACCEPTED","DUPLICATE"),jobs.map{it.get(20,TimeUnit.SECONDS)}.sorted());assertEquals(1,count())
            // Hold user NO KEY UPDATE in independent connection; FIFO is not assumed.
            jdbc.dataSource!!.connection.use{blocker->blocker.autoCommit=false
                blocker.prepareStatement("SELECT id FROM users WHERE id=? FOR NO KEY UPDATE").use{it.setLong(1,account);it.executeQuery().close()}
                val batch=pool.submit<String>{analytics.batch(account,BatchRequest(listOf(event()))).results.single().status}
                val disable=pool.submit<ConsentState>{analytics.updateConsent(account,ConsentRequest(false,null,1))}
                blocker.commit();val result=batch.get(20,TimeUnit.SECONDS);disable.get(20,TimeUnit.SECONDS)
                assertTrue(result in setOf("ACCEPTED","DROPPED_OPT_OUT"));assertEquals(if(result=="ACCEPTED")2 else 1,count())
                assertEquals("DROPPED_OPT_OUT",analytics.batch(account,input).results.single().status)
            }
        }finally{pool.shutdownNow()}
    }
    @Test fun `retention filters before prune and direct D1 D7 uses event timezone without stored anchors`() {
        optIn();val now=Instant.now();val first=now.minusSeconds(10L*86400)
        for(at in listOf(first,first.plusSeconds(86400),first.plusSeconds(7L*86400)))assertEquals("ACCEPTED",analytics.batch(account,BatchRequest(listOf(event(at=at)))).results.single().status)
        val expired=event(at=now.minusSeconds(91L*86400));assertEquals("DROPPED_EXPIRED",analytics.batch(account,BatchRequest(listOf(expired))).results.single().status)
        val oldId=UUID.randomUUID();jdbc.update("INSERT INTO analytics_events(id,user_id,event_name,session_id,occurred_at,received_at,app_version,timezone,properties,payload_hash,consent_revision) VALUES (?,?,'app_open',?,?,?,'old','UTC','{}','old',1)",oldId,account,UUID.randomUUID(),Timestamp.from(now.minusSeconds(91L*86400)),Timestamp.from(now.minusSeconds(91L*86400)))
        val queryNow=Instant.now()
        var report=analytics.retention(queryNow);assertTrue(report.returnedD1>=1);assertTrue(report.returnedD7>=1)
        val before=report;assertEquals(1,analytics.prune(queryNow));assertEquals(before,analytics.retention(queryNow));assertEquals(3,count())
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='learner_settings' AND column_name LIKE '%anchor%'",Int::class.java))
        // Three same local dates have no day+1 return, regardless of UUID count.
        jdbc.update("UPDATE analytics_events SET occurred_at=?,received_at=?,timezone='America/New_York' WHERE user_id=?",Timestamp.from(now.minusSeconds(4L*86400)),Timestamp.from(now),account)
        report=analytics.retention(queryNow);assertTrue(report.returnedD1 < before.returnedD1)
        jdbc.update("INSERT INTO user_roles SELECT ?,id FROM roles WHERE name='ROLE_ADMIN'",account)
        mvc.perform(get("/api/v1/admin/analytics/retention").header("Authorization","Bearer $token")).andExpect(status().isOk).andExpect(jsonPath("$.data.windowDays").value(90))
    }
    @Test fun `direct report respects DST calendar day and excludes immature observations`() {
        optIn()
        val now=Instant.parse("2026-03-20T12:00:00Z")
        fun raw(at:String,zone:String){jdbc.update("INSERT INTO analytics_events(id,user_id,event_name,session_id,occurred_at,received_at,app_version,timezone,properties,payload_hash,consent_revision) VALUES (?,?,'app_open',?,?,?,'test',?,'{}','fixture',1)",UUID.randomUUID(),account,UUID.randomUUID(),Timestamp.from(Instant.parse(at)),Timestamp.from(now),zone)}
        raw("2026-03-08T05:30:00Z","America/New_York") // March8 00:30 EST
        raw("2026-03-09T04:30:00Z","America/New_York") // March9 00:30 EDT, 23h later
        raw("2026-03-15T04:30:00Z","America/New_York")
        val report=analytics.retention(now);assertEquals(1L,report.eligibleD1);assertEquals(1L,report.returnedD1);assertEquals(1L,report.eligibleD7);assertEquals(1L,report.returnedD7)
        jdbc.update("UPDATE analytics_events SET occurred_at=? WHERE user_id=?",Timestamp.from(now),account)
        val immature=analytics.retention(now);assertEquals(0L,immature.eligibleD1);assertEquals(0L,immature.eligibleD7);assertNull(immature.d1Rate)
    }
    @Test fun `analytics FK cascades only deleted account and keeps other events`() {
        optIn();analytics.batch(account,BatchRequest(listOf(event())))
        val other=register("survivor-${UUID.randomUUID()}@example.invalid","Other")
        analytics.updateConsent(other,ConsentRequest(true,"analytics-v1",0));analytics.batch(other,BatchRequest(listOf(event())))
        TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)).executeWithoutResult{jdbc.update("DELETE FROM learner_settings WHERE user_id=?",account);jdbc.update("DELETE FROM users WHERE id=?",account)}
        assertEquals(0,count());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE user_id=?",Int::class.java,other))
    }
    private fun rawOpen(at:Instant,received:Instant,zone:String){
        jdbc.update("INSERT INTO analytics_events(id,user_id,event_name,session_id,occurred_at,received_at,app_version,timezone,properties,payload_hash,consent_revision) VALUES (?,?,'app_open',?,?,?,'test',?,'{}','fixture',1)",
            UUID.randomUUID(),account,UUID.randomUUID(),Timestamp.from(at),Timestamp.from(received),zone)
    }
    @Test fun `New York 23 and 25 hour DST days count calendar D1 and D7`() {
        optIn()
        val fixtures=listOf(
            listOf("2026-03-08T05:30:00Z","2026-03-09T04:30:00Z","2026-03-15T04:30:00Z","2026-03-20T12:00:00Z","23"),
            listOf("2026-11-01T04:30:00Z","2026-11-02T05:30:00Z","2026-11-08T05:30:00Z","2026-11-15T12:00:00Z","25"))
        for(fixture in fixtures){
            jdbc.update("DELETE FROM analytics_events WHERE user_id=?",account)
            val times=fixture.take(4).map(Instant::parse);val now=times[3]
            assertEquals(fixture[4].toLong(),java.time.Duration.between(times[0],times[1]).toHours())
            val before=analytics.retention(now)
            times.take(3).forEach{rawOpen(it,now,"America/New_York")}
            val report=analytics.retention(now)
            assertEquals(before.eligibleD1+1,report.eligibleD1);assertEquals(before.returnedD1+1,report.returnedD1)
            assertEquals(before.eligibleD7+1,report.eligibleD7);assertEquals(before.returnedD7+1,report.returnedD7)
        }
        jdbc.update("DELETE FROM analytics_events WHERE user_id=?",account)
    }
    @Test fun `same instants use distinct New York and Ho Chi Minh calendar dates`() {
        optIn();val now=Instant.parse("2026-03-20T12:00:00Z")
        val times=listOf("2026-03-08T05:30:00Z","2026-03-08T18:30:00Z","2026-03-15T05:30:00Z").map(Instant::parse)
        for(zone in listOf("America/New_York","Asia/Ho_Chi_Minh")){
            jdbc.update("DELETE FROM analytics_events WHERE user_id=?",account)
            val before=analytics.retention(now);times.forEach{rawOpen(it,now,zone)}
            val report=analytics.retention(now)
            assertEquals(before.eligibleD1+1,report.eligibleD1)
            assertEquals(before.returnedD1+if(zone=="Asia/Ho_Chi_Minh")1 else 0,report.returnedD1)
            assertEquals(before.returnedD7+1,report.returnedD7)
        }
        jdbc.update("DELETE FROM analytics_events WHERE user_id=?",account)
    }
    @Test fun `offline app opens arriving D7 D1 then first day use occurred order and frozen UUID`() {
        optIn();val now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        val first=now.minusSeconds(10L*86400);val dates=listOf(first.plusSeconds(7L*86400),first.plusSeconds(86400),first)
        val baseline=analytics.retention(now)
        val inputs=dates.map{at->event(at=at).apply{put("timezone","Asia/Ho_Chi_Minh");putObject("properties").put("offline",true)}}
        for(input in inputs)assertEquals("ACCEPTED",analytics.batch(account,BatchRequest(listOf(input))).results.single().status)
        assertEquals(dates.map(Timestamp::from),jdbc.queryForList("SELECT occurred_at FROM analytics_events WHERE user_id=? ORDER BY received_at,id",Timestamp::class.java,account))
        val after=analytics.retention(Instant.now())
        assertEquals(baseline.eligibleD1+1,after.eligibleD1);assertEquals(baseline.returnedD1+1,after.returnedD1)
        assertEquals(baseline.eligibleD7+1,after.eligibleD7);assertEquals(baseline.returnedD7+1,after.returnedD7)
        for(input in inputs)assertEquals("DUPLICATE",analytics.batch(account,BatchRequest(listOf(input))).results.single().status)
        assertEquals(3,count())
        jdbc.update("DELETE FROM analytics_events WHERE user_id=?",account)
    }
    private fun lessonFixture():List<UUID>{
        ContentSeedImporter(jdbc,mapper,TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)))
            .import(requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes(),setOf("test"))
        lessons.enroll(account,UUID.fromString("00000000-0000-4000-8000-000000000002"))
        return jdbc.queryForList("SELECT r.id FROM lesson_revisions r JOIN lessons l ON l.id=r.lesson_id WHERE r.status='PUBLISHED' ORDER BY l.position",UUID::class.java)
    }
    private fun attempt(revision:UUID,mode:String="LESSON")=SubmitLessonRequest(UUID.randomUUID(),revision,mode,System.currentTimeMillis(),true,
        jdbc.queryForList("SELECT id,answer_key::text FROM questions WHERE lesson_revision_id=? ORDER BY position",revision).map{SubmittedAnswer(it["id"] as UUID,mapper.readTree(it["answer_key"].toString())["optionKey"].asText())})
    @Test fun `server emits passed once only for opted confirmed LESSON and not waiting or practice`() {
        optIn();val revisions=lessonFixture();val pending=attempt(revisions[1]);lessons.submit(account,pending);assertEquals(0,count())
        lessons.submit(account,attempt(revisions[0],"PRACTICE"));assertEquals(0,count())
        val first=attempt(revisions[0]);assertEquals(30,lessons.submit(account,first).xpAwarded);assertEquals(1,count());lessons.submit(account,first);assertEquals(1,count())
        assertEquals("CONFIRMED",lessons.submit(account,pending).confirmationStatus);assertEquals(2,count());lessons.submit(account,pending);assertEquals(2,count())
        analytics.updateConsent(account,ConsentRequest(false,null,1));assertEquals(30,lessons.submit(account,attempt(revisions[2])).xpAwarded);assertEquals(2,count())
    }
    @Test fun `analytics insert failure cannot rollback official attempt progress or XP`() {
        optIn();val revisions=lessonFixture()
        jdbc.execute("""CREATE FUNCTION fail_analytics() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'Injected analytics failure'; END; ${'$'}${'$'}""")
        jdbc.execute("CREATE TRIGGER fail_analytics BEFORE INSERT ON analytics_events FOR EACH ROW EXECUTE FUNCTION fail_analytics()")
        try{val input=attempt(revisions[0]);assertEquals(30,lessons.submit(account,input).xpAwarded);assertEquals(30,lessons.result(account,input.id).xpAwarded);assertEquals(0,count())}
        finally{jdbc.execute("DROP TRIGGER fail_analytics ON analytics_events");jdbc.execute("DROP FUNCTION fail_analytics()")}
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM lesson_progress WHERE user_id=?",Int::class.java,account))
    }
    @Test fun `failed grades and rolled back business transactions never emit passed`() {
        optIn();val revisions=lessonFixture();val correct=attempt(revisions[0]);val wrong=correct.copy(answers=correct.answers.map{it.copy(optionKey="B")})
        assertFalse(lessons.submit(account,wrong).passed);assertEquals(0,count())
        jdbc.execute("""CREATE FUNCTION fail_analytics_business() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'Injected XP failure'; END; ${'$'}${'$'}""")
        jdbc.execute("CREATE TRIGGER fail_analytics_business BEFORE INSERT ON xp_events FOR EACH ROW EXECUTE FUNCTION fail_analytics_business()")
        try{assertThrows(Exception::class.java){lessons.submit(account,attempt(revisions[0]))};assertEquals(0,count());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM xp_events WHERE user_id=?",Int::class.java,account))}
        finally{jdbc.execute("DROP TRIGGER fail_analytics_business ON xp_events");jdbc.execute("DROP FUNCTION fail_analytics_business()")}
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        @DynamicPropertySource @JvmStatic fun database(registry:DynamicPropertyRegistry){
            registry.add("spring.datasource.url",postgres::getJdbcUrl);registry.add("spring.datasource.username",postgres::getUsername);registry.add("spring.datasource.password",postgres::getPassword)
            registry.add("spring.datasource.driver-class-name"){"org.postgresql.Driver"}
        }
    }
}
