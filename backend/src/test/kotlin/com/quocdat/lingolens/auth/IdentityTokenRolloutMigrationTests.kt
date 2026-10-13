package com.quocdat.lingolens.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.quocdat.lingolens.curriculum.ContentSeedImporter
import com.quocdat.lingolens.integration.MigrationChecksums
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@Testcontainers @Tag("postgres")
class IdentityTokenRolloutMigrationTests {
    @Test fun `V13 rollout revokes legacy refresh once and preserves every other account and learning field`() {
        val source=DriverManagerDataSource(postgres.jdbcUrl,postgres.username,postgres.password);val jdbc=JdbcTemplate(source)
        source.connection.use{assertEquals("PostgreSQL",it.metaData.databaseProductName);assertTrue(it.metaData.url.startsWith("jdbc:postgresql:"))}
        fun migrate(target:String)=Flyway.configure().dataSource(source).target(target).load().also{it.migrate();it.validate()}
        migrate("13")
        ContentSeedImporter(jdbc,jacksonObjectMapper(),TransactionTemplate(DataSourceTransactionManager(source)))
            .import(requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes(),setOf("test"))
        for((id,level,goal) in listOf(Triple(101L,"B1",8),Triple(102L,"B2",17))){
            jdbc.update("INSERT INTO users(id,email,password,name,target_level,daily_goal) VALUES (?,?,'unchanged-password','Legacy',?,?)",id,"legacy-$id@example.invalid",level,goal)
            jdbc.update("INSERT INTO user_roles SELECT ?,id FROM roles",id)
            jdbc.update("INSERT INTO learner_settings(user_id,target_level,daily_new_word_goal) VALUES (?,?,?)",id,level,goal)
            jdbc.update("INSERT INTO refresh_tokens(token_hash,user_id,expiry_date,device_info) VALUES (?,?,'2030-01-01','unchanged-device')","old-refresh-$id",id)
            jdbc.update("INSERT INTO learning_entries VALUES (?,'hello','{}',1000,1000,FALSE)",id)
            jdbc.update("INSERT INTO review_cards(user_id,word,due_at,repetitions) VALUES (?,'hello',2000,2)",id)
            jdbc.update("INSERT INTO review_events VALUES (?,?,'hello','GOOD',1000)",id,UUID.randomUUID().toString())
            jdbc.update("INSERT INTO sync_receipts(user_id,operation_id,request_hash,operation_kind) VALUES (?,?,'unchanged','SAVE_WORD')",id,UUID.randomUUID().toString())
        }
        jdbc.update("INSERT INTO refresh_tokens(token_hash,user_id,expiry_date,revoked_at) VALUES ('already-revoked',102,'2030-01-01','2025-01-01')")
        val course=UUID.fromString("00000000-0000-4000-8000-000000000002");val revision=UUID.fromString("00000000-0000-4000-8000-000000000020")
        val lesson=UUID.fromString("00000000-0000-4000-8000-000000000010");val attempt=UUID.randomUUID()
        jdbc.update("INSERT INTO enrollments VALUES (101,?,'ACTIVE','2025-01-01',NULL)",course)
        jdbc.update("INSERT INTO lesson_attempts(id,user_id,lesson_revision_id,mode,score_percent,passed,submitted_offline,client_completed_at,received_at,request_hash) VALUES (?,101,?,'LESSON',100,TRUE,FALSE,'2025-01-02','2025-01-02','unchanged')",attempt,revision)
        jdbc.queryForList("SELECT id FROM questions WHERE lesson_revision_id=?",UUID::class.java,revision).forEach{q->
            jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,CAST(? AS JSONB),TRUE,1)",attempt,q,revision,"""{"optionKey":"A"}""")}
        jdbc.update("INSERT INTO lesson_progress VALUES (101,?,100,'2025-01-02',?,'2025-01-02')",lesson,attempt)
        jdbc.update("INSERT INTO xp_events VALUES (?,101,'LESSON_FIRST_PASS',?,30,1,'2025-01-02')",UUID.randomUUID(),lesson.toString())
        val tables=listOf("users","roles","user_roles","learner_settings","learning_entries","review_cards","review_events","sync_receipts","enrollments","lesson_attempts","attempt_answers","lesson_progress","xp_events","topics","topic_vocabularies","courses","course_units","lessons","lesson_revisions","lesson_vocabularies","questions","question_options","content_import_receipts")
        fun snapshot()=tables.associateWith{table->jdbc.queryForList("SELECT row_to_json(t)::text FROM $table t ORDER BY row_to_json(t)::text",String::class.java)}
        val before=snapshot()
        fun tokenFields()=jdbc.queryForList("SELECT (to_jsonb(t)-'revoked_at')::text FROM refresh_tokens t ORDER BY id",String::class.java)
        val tokenBefore=tokenFields();val already=jdbc.queryForObject("SELECT revoked_at FROM refresh_tokens WHERE token_hash='already-revoked'",java.sql.Timestamp::class.java)
        val history=jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank")
        val indexes=jdbc.queryForList("SELECT indexname,indexdef FROM pg_indexes WHERE indexname IN ('uq_lesson_published_revision','uq_user_active_enrollment') ORDER BY indexname")
        val node=jdbc.queryForObject("SELECT pg_relation_filenode('refresh_tokens'::regclass)",Long::class.java)
        migrate("14")
        assertEquals(before,snapshot());assertEquals(tokenBefore,tokenFields());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL",Int::class.java))
        assertEquals(already,jdbc.queryForObject("SELECT revoked_at FROM refresh_tokens WHERE token_hash='already-revoked'",java.sql.Timestamp::class.java))
        assertEquals(history,jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL AND version<>'14' ORDER BY installed_rank"))
        assertEquals(indexes,jdbc.queryForList("SELECT indexname,indexdef FROM pg_indexes WHERE indexname IN ('uq_lesson_published_revision','uq_user_active_enrollment') ORDER BY indexname"))
        assertEquals(node,jdbc.queryForObject("SELECT pg_relation_filenode('refresh_tokens'::regclass)",Long::class.java))
        assertEquals(30,jdbc.queryForObject("SELECT sum(amount) FROM xp_events WHERE user_id=101",Int::class.java))
        // Re-running Flyway/startup must never revoke tokens issued after the one-time rollout.
        jdbc.update("INSERT INTO refresh_tokens(token_hash,user_id,expiry_date) VALUES ('new-after-rollout',101,'2030-01-01')")
        migrate("14");assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash='new-after-rollout' AND revoked_at IS NULL",Int::class.java))
        assertTrue(jdbc.queryForObject("SELECT checksum IS NOT NULL FROM flyway_schema_history WHERE version='14' AND success",Boolean::class.java)!!)
        MigrationChecksums.assertCommittedSourcesUnchanged();assertRolloutSourceUnchanged()
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        fun assertRolloutSourceUnchanged(){
            val root=generateSequence(File(System.getProperty("user.dir")).absoluteFile){it.parentFile}
                .first{File(it,"docs/portfolio/week8-jwt-migration-checksum.json").isFile}
            val manifest=jacksonObjectMapper().readTree(File(root,"docs/portfolio/week8-jwt-migration-checksum.json"))
            val source=File(root,manifest["path"].asText()).readText(Charsets.UTF_8).replace("\r\n","\n").toByteArray(Charsets.UTF_8)
            val hash=MessageDigest.getInstance("SHA-256").digest(source).joinToString(""){"%02x".format(it)}
            assertEquals(manifest["sha256Lf"].asText(),hash)
        }
    }
}
