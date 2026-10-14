package com.quocdat.lingolens.deletion

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
import java.time.Instant
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.*

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @Tag("postgres")
class DeletionPostgresTests {
    @Autowired lateinit var jdbc:JdbcTemplate
    @Autowired lateinit var auth:AuthService
    @Autowired lateinit var service:DeletionService
    @Autowired lateinit var mvc:MockMvc
    private val created=mutableListOf<Long>()
    private fun code()=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)})
    private fun account():Pair<Long,String>{
        val email="delete-${UUID.randomUUID()}@example.invalid"
        return (auth.register(RegisterRequest(email,"Secure123!","Deletion fixture")).id!!.also{created.add(it)}) to email
    }
    private fun snapshot(id:Long)=listOf("users","learner_settings","user_roles","refresh_tokens","learning_entries","review_cards","review_events","sync_receipts","enrollments","lesson_attempts","lesson_progress","xp_events","analytics_events","recognition_history","recognition_reports")
        .associateWith {table->jdbc.queryForList("SELECT * FROM $table WHERE ${if(table=="users")"id" else "user_id"}=?",id)}
    private fun fixture(id:Long){
        ContentSeedImporter(jdbc,jacksonObjectMapper(),TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)))
            .import(requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes(),setOf("test"))
        val course=UUID.fromString("00000000-0000-4000-8000-000000000002")
        val revision=UUID.fromString("00000000-0000-4000-8000-000000000020")
        val attempt=UUID.randomUUID()
        jdbc.update("INSERT INTO enrollments(user_id,course_id,status,enrolled_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP)",id,course)
        jdbc.update("INSERT INTO lesson_attempts(id,user_id,lesson_revision_id,mode,score_percent,passed,submitted_offline,client_completed_at,received_at,request_hash) VALUES (?,?,?,'LESSON',75,TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'fixture')",attempt,id,revision)
        jdbc.update("INSERT INTO attempt_answers(attempt_id,question_id,lesson_revision_id,submitted_answer,is_correct,awarded_weight) SELECT ?,id,lesson_revision_id,'{}',TRUE,1 FROM questions WHERE lesson_revision_id=?",attempt,revision)
        val lesson=jdbc.queryForObject("SELECT lesson_id FROM lesson_revisions WHERE id=?",UUID::class.java,revision)
        jdbc.update("INSERT INTO lesson_progress(user_id,lesson_id,best_score_percent,first_passed_at,last_attempt_id,updated_at) VALUES (?,?,75,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",id,lesson,attempt)
        jdbc.update("INSERT INTO xp_events(id,user_id,reason,source_key,amount,rules_version,awarded_at) VALUES (?,?,'LESSON_FIRST_PASS',?,30,1,CURRENT_TIMESTAMP)",UUID.randomUUID(),id,lesson.toString())
        jdbc.update("INSERT INTO learning_entries VALUES (?,'book','{}',1000,1000,FALSE)",id)
        jdbc.update("INSERT INTO review_cards(user_id,word,due_at) VALUES (?,'book',2000)",id)
        jdbc.update("INSERT INTO review_events VALUES (?,?,'book','GOOD',1000)",id,UUID.randomUUID().toString())
        jdbc.update("INSERT INTO sync_receipts(user_id,operation_id,request_hash,operation_kind) VALUES (?,?,'fixture','SAVE_WORD')",id,UUID.randomUUID().toString())
        jdbc.update("INSERT INTO analytics_events(id,user_id,event_name,session_id,occurred_at,received_at,app_version,timezone,properties,payload_hash,consent_revision) VALUES (?,?,'app_open',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'test','UTC','{}',?,1)",UUID.randomUUID(),id,UUID.randomUUID(),"a".repeat(64))
        jdbc.update("INSERT INTO recognition_history(user_id,detected_label,confidence) VALUES (?,'book',0.9)",id)
        jdbc.update("INSERT INTO recognition_reports(user_id,expected_label,actual_label,note) VALUES (?,'book','bottle','fixture')",id)
    }
    @AfterEach fun cleanup(){created.forEach{id->if(jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Int::class.java,id)!!>0)service.delete(id,"Secure123!",code())};jdbc.update("DELETE FROM account_deletion_receipts")}
    @Test fun `password confirmed delete removes owned rows and answers without orphan or changing other account`() {
        jdbc.dataSource!!.connection.use{assertEquals("PostgreSQL",it.metaData.databaseProductName)}
        val (a,email)=account();val (b,_)=account();fixture(a);fixture(b)
        val token=auth.login(LoginRequest(email,"Secure123!"),null)
        val other=snapshot(b);val shared=jdbc.queryForList("SELECT * FROM vocabularies ORDER BY id")
        jdbc.update("INSERT INTO admin_audit_logs(admin_user_id,admin_email,action,target_type,target_id,details) VALUES (?,?,'UPDATE','USER',?,'PII fixture')",b,"other@example.invalid",a.toString())
        val attempts=jdbc.queryForList("SELECT id FROM lesson_attempts WHERE user_id=?",UUID::class.java,a)
        val proof=code()
        mvc.perform(post("/api/v1/users/me/deletion").header("Authorization","Bearer ${token.accessToken}")
            .contentType("application/json").content(jacksonObjectMapper().writeValueAsString(DeleteAccountRequest("Secure123!",proof))))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("DELETED"))
        snapshot(a).forEach{(table,rows)->assertTrue(rows.isEmpty(),table)}
        attempts.forEach{assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE attempt_id=?",Int::class.java,it)!!)}
        assertEquals(other,snapshot(b));assertEquals(shared,jdbc.queryForList("SELECT * FROM vocabularies ORDER BY id"))
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM admin_audit_logs WHERE target_type='USER' AND target_id=?",Int::class.java,a.toString())!!)
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM attempt_answers aa LEFT JOIN lesson_attempts a ON a.id=aa.attempt_id WHERE a.id IS NULL",Int::class.java)!!)
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer ${token.accessToken}")).andExpect(status().isUnauthorized)
        assertThrows(Exception::class.java){auth.refresh(RefreshTokenRequest(token.refreshToken),null)}
        val replacement=auth.register(RegisterRequest(email,"Secure123!","New identity")).id!!.also{created.add(it)}
        assertNotEquals(a,replacement);assertTrue(jdbc.queryForList("SELECT * FROM learning_entries WHERE user_id=?",replacement).isEmpty())
    }
    @Test fun `wrong password and failed delete roll back all rows and never create receipt`() {
        val (a,_)=account();fixture(a);val before=snapshot(a);val proof=code()
        assertThrows(Exception::class.java){service.delete(a,"wrong",proof)}
        assertEquals(before,snapshot(a));assertEquals("UNKNOWN",service.status(proof).status)
        assertThrows(IllegalStateException::class.java){service.delete(a,"Secure123!",proof,afterDelete={error("Injected failure")})}
        assertEquals(before,snapshot(a));assertEquals("UNKNOWN",service.status(proof).status)
        assertTrue(jdbc.queryForList("SELECT * FROM account_deletion_receipts").isEmpty())
    }
    @Test fun `lost response and same code retry use anonymous status without exposing identity`() {
        val (a,_)=account();val proof=code();service.delete(a,"Secure123!",proof)
        assertEquals("DELETED",service.delete(a,"Secure123!",proof).status)
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM account_deletion_receipts",Int::class.java)!!)
        for(value in listOf(proof,code(),"invalid")){
            val response=mvc.perform(post("/api/v1/account-deletion/status").contentType("application/json")
                .content(jacksonObjectMapper().writeValueAsString(ReceiptStatusRequest(value))))
                .andExpect(status().isOk).andReturn().response.contentAsString
            val data=jacksonObjectMapper().readTree(response)["data"]
            assertEquals(listOf("status"),data.fieldNames().asSequence().toList())
            assertEquals(if(value==proof)"DELETED" else "UNKNOWN",data["status"].asText())
        }
        val row=jdbc.queryForMap("SELECT * FROM account_deletion_receipts")
        assertEquals(setOf("code_hash","status","expires_at"),row.keys);assertFalse(row.values.any{it.toString().contains(proof)})
        val (b,_)=account();assertThrows(Exception::class.java){service.delete(b,"Secure123!",proof)}
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Int::class.java,b)!!)
    }
    @Test fun `receipt expires exactly at seven days and prune removes expired only`() {
        val (a,_)=account();val start=Instant.parse("2030-01-01T00:00:00Z");val proof=code()
        service.delete(a,"Secure123!",proof,now=start)
        assertEquals("DELETED",service.status(proof,start.plusSeconds(7*86400-1L)).status)
        assertEquals("UNKNOWN",service.status(proof,start.plusSeconds(7*86400L)).status)
        assertEquals(0,service.prune(start.plusSeconds(7*86400-1L)))
        assertEquals(1,service.prune(start.plusSeconds(7*86400L)));assertEquals("UNKNOWN",service.status(proof,start).status)
    }
    @Test fun `two concurrent deletions same proof are idempotent with PostgreSQL locks`() {
        val (a,_)=account();fixture(a);val proof=code();val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
        try{val calls=(1..2).map{pool.submit<String>{start.await();service.delete(a,"Secure123!",proof).status}}
            start.countDown();calls.forEach{assertEquals("DELETED",it.get(30,TimeUnit.SECONDS))}
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM account_deletion_receipts",Int::class.java)!!)
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Int::class.java,a)!!)
        }finally{pool.shutdownNow()}
    }
    @Test fun `login FK insert concurrent with deletion cannot recreate token or orphan account data`() {
        val (a,email)=account();val locked=CountDownLatch(1);val release=CountDownLatch(1);val loginStarted=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(2)
        try{
            val deletion=pool.submit<String>{service.delete(a,"Secure123!",code(),afterDelete={locked.countDown();assertTrue(release.await(20,TimeUnit.SECONDS))}).status}
            assertTrue(locked.await(20,TimeUnit.SECONDS))
            val login=pool.submit<Boolean>{loginStarted.countDown();try{auth.login(LoginRequest(email,"Secure123!"),null);true}catch(_:Exception){false}}
            assertTrue(loginStarted.await(20,TimeUnit.SECONDS));release.countDown()
            assertEquals("DELETED",deletion.get(30,TimeUnit.SECONDS));assertFalse(login.get(30,TimeUnit.SECONDS))
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id=?",Int::class.java,a)!!)
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM refresh_tokens t LEFT JOIN users u ON u.id=t.user_id WHERE u.id IS NULL",Int::class.java)!!)
        }finally{release.countDown();pool.shutdownNow()}
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        @DynamicPropertySource @JvmStatic fun database(r:DynamicPropertyRegistry){
            r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername)
            r.add("spring.datasource.password",postgres::getPassword);r.add("spring.datasource.driver-class-name"){"org.postgresql.Driver"}
        }
    }
}
