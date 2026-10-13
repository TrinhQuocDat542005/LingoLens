package com.quocdat.lingolens.assessment

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
import java.util.UUID

@Testcontainers @Tag("postgres")
class AttemptConfirmationMigrationTests {
    @Test fun `V12 to V13 preserves every week5 row checksums indexes and physical heap`() {
        val source=DriverManagerDataSource(postgres.jdbcUrl,postgres.username,postgres.password)
        val jdbc=JdbcTemplate(source)
        fun flyway(target:String)=Flyway.configure().dataSource(source).target(target).load()
        flyway("12").migrate()
        val mapper=jacksonObjectMapper()
        ContentSeedImporter(jdbc,mapper,TransactionTemplate(DataSourceTransactionManager(source)))
            .import(requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes(),setOf("test"))
        jdbc.update("INSERT INTO users(id,email,password,name,target_level,daily_goal) VALUES (101,'v12@example.invalid','untouched-hash','Legacy','B2',17)")
        jdbc.update("INSERT INTO user_roles SELECT 101,id FROM roles")
        jdbc.update("INSERT INTO learner_settings(user_id,target_level,daily_new_word_goal) VALUES (101,'B2',17)")
        jdbc.update("INSERT INTO refresh_tokens(token_hash,user_id,expiry_date) VALUES ('preserved-token',101,'2030-01-01')")
        jdbc.update("INSERT INTO learning_entries VALUES (101,'hello','{}',1700000000000,1700000000000,FALSE)")
        jdbc.update("INSERT INTO review_cards(user_id,word,due_at,repetitions) VALUES (101,'hello',1700086400000,2)")
        jdbc.update("INSERT INTO sync_receipts(user_id,operation_id,request_hash,operation_kind) VALUES (101,'legacy-op','untouched','SAVE_WORD')")
        val course=UUID.fromString("00000000-0000-4000-8000-000000000002")
        val revision=UUID.fromString("00000000-0000-4000-8000-000000000020")
        val lesson=UUID.fromString("00000000-0000-4000-8000-000000000010")
        jdbc.update("INSERT INTO enrollments VALUES (101,?,'ACTIVE','2025-01-01',NULL)",course)
        val attempts=listOf(UUID.randomUUID(),UUID.randomUUID())
        attempts.forEachIndexed { i,id ->
            jdbc.update("INSERT INTO lesson_attempts VALUES (?,101,?,'LESSON',?, ?,FALSE,'2025-01-02','2025-01-02',?)",id,revision,if(i==0) 100 else 0,i==0,"hash-$i")
            jdbc.queryForList("SELECT id,answer_key::text FROM questions WHERE lesson_revision_id=?",revision).forEach{q ->
                val answer=mapper.readTree(q["answer_key"].toString())
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,CAST(? AS JSONB),?,?)",id,q["id"],revision,mapper.writeValueAsString(answer),i==0,if(i==0) 1 else 0)
            }
        }
        jdbc.update("INSERT INTO lesson_progress VALUES (101,?,100,'2025-01-02',?,'2025-01-02')",lesson,attempts[1])
        jdbc.update("INSERT INTO xp_events VALUES (?,101,'LESSON_FIRST_PASS',?,30,1,'2025-01-02')",attempts[0],lesson.toString())
        val tables=listOf("users","roles","user_roles","learner_settings","refresh_tokens","learning_entries","review_cards","sync_receipts","enrollments","lesson_attempts","attempt_answers","lesson_progress","xp_events")
        fun snapshot(after:Boolean)=tables.associateWith { table ->
            val json=if(after && table=="lesson_attempts") "to_jsonb(t)-'confirmation_status'" else "to_jsonb(t)"
            jdbc.queryForList("SELECT ($json)::text FROM $table t ORDER BY ($json)::text",String::class.java)
        }
        val before=snapshot(false)
        val history=jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank")
        val heap=jdbc.queryForObject("SELECT pg_relation_filenode('lesson_attempts'::regclass)",Long::class.java)
        val indexSql=jdbc.queryForList("SELECT indexname,indexdef FROM pg_indexes WHERE indexname IN ('uq_lesson_published_revision','uq_user_active_enrollment') ORDER BY indexname")
        flyway("13").migrate();flyway("13").validate()
        assertEquals(before,snapshot(true));assertEquals(30,jdbc.queryForObject("SELECT sum(amount) FROM xp_events WHERE user_id=101",Int::class.java)!!)
        assertEquals(listOf("CONFIRMED","CONFIRMED"),jdbc.queryForList("SELECT confirmation_status FROM lesson_attempts ORDER BY id",String::class.java))
        assertEquals(heap,jdbc.queryForObject("SELECT pg_relation_filenode('lesson_attempts'::regclass)",Long::class.java))
        assertEquals(history,jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL AND version<>'13' ORDER BY installed_rank"))
        assertEquals(indexSql,jdbc.queryForList("SELECT indexname,indexdef FROM pg_indexes WHERE indexname IN ('uq_lesson_published_revision','uq_user_active_enrollment') ORDER BY indexname"))
        assertEquals(2,indexSql.size)
        // A second published revision and a second active enrollment must still fail on PostgreSQL.
        val duplicate=UUID.randomUUID()
        jdbc.update("INSERT INTO lesson_revisions(id,lesson_id,revision_no,introduction,sentence_patterns,status,created_at,content_status,pack_id) SELECT ?,lesson_id,99,introduction,sentence_patterns,'DRAFT',created_at,content_status,pack_id FROM lesson_revisions WHERE id=?",duplicate,revision)
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java){jdbc.update("UPDATE lesson_revisions SET status='PUBLISHED' WHERE id=?",duplicate)}
        val secondCourse=UUID.randomUUID()
        jdbc.update("INSERT INTO courses(id,code,title,description,level,status,created_at,content_status,pack_id) SELECT ?,'second-course',title,description,level,status,created_at,content_status,pack_id FROM courses WHERE id=?",secondCourse,course)
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java){jdbc.update("INSERT INTO enrollments VALUES (101,?,'ACTIVE',CURRENT_TIMESTAMP,NULL)",secondCourse)}
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java){jdbc.update("UPDATE lesson_attempts SET confirmation_status='INVALID' WHERE id=?",attempts[0])}
        MigrationChecksums.assertCommittedSourcesUnchanged()
        val checksum=jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='13' AND success",Int::class.java)
        assertNotNull(checksum);println("V13 Flyway checksum=$checksum; V12 row snapshots preserved; heap filenode unchanged")
    }
    companion object { @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine") }
}
