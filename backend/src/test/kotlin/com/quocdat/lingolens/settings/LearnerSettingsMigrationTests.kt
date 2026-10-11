package com.quocdat.lingolens.settings

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@Tag("postgres")
class LearnerSettingsMigrationTests {
    private lateinit var jdbc: JdbcTemplate
    private fun flyway(target: String) = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password).target(target).cleanDisabled(false).load()

    @BeforeEach fun atV8() {
        flyway("8").clean(); flyway("8").migrate()
        jdbc = JdbcTemplate(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password))
    }

    @Test fun `V9 preserves legacy values and all V8 user data`() {
        account(11, "B1", 8); account(12, "B2", 23); account(13, "A1", 1)
        jdbc.update("INSERT INTO user_roles SELECT 11,id FROM roles")
        jdbc.update("INSERT INTO refresh_tokens(token_hash,user_id,expiry_date) VALUES ('unchanged-token',11,'2030-01-01')")
        jdbc.update("INSERT INTO learning_entries VALUES (11,'book','{}',1700000000000,1700000000000,FALSE),(11,'dog',NULL,NULL,NULL,TRUE)")
        jdbc.update("INSERT INTO review_cards(user_id,word,due_at,repetitions) VALUES (11,'book',1700086400000,2)")
        jdbc.update("INSERT INTO review_events VALUES (11,'00000000-0000-0000-0000-000000000001','book','GOOD',1700000000000)")
        jdbc.update("INSERT INTO sync_receipts(user_id,operation_id,request_hash,operation_kind) VALUES (11,'00000000-0000-0000-0000-000000000001','hash','REVIEW_WORD')")
        val tables = listOf("users", "roles", "user_roles", "refresh_tokens", "learning_entries", "review_cards", "review_events", "sync_receipts")
        fun snapshot() = tables.associateWith { jdbc.queryForList("SELECT row_to_json(t)::text FROM $it t ORDER BY row_to_json(t)::text", String::class.java) }
        val before = snapshot()
        flyway("9").migrate()
        assertEquals(before, snapshot())
        assertEquals(listOf("B1:8:UNSET:10", "B2:23:UNSET:10", "A1:1:UNSET:10"), jdbc.queryForList(
            "SELECT target_level || ':' || daily_new_word_goal || ':' || learning_goal || ':' || daily_review_goal FROM learner_settings ORDER BY user_id", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM learner_settings WHERE timezone IS NOT NULL OR onboarding_completed_at IS NOT NULL", Int::class.java))
    }

    @Test fun `unsupported level aborts V9 atomically with an explanatory constraint`() { invalid("C1", 5, "target_level") }
    @Test fun `null level aborts V9 atomically with an explanatory column`() { invalid(null, 5, "target_level") }
    @Test fun `invalid goal aborts V9 atomically with an explanatory constraint`() { invalid("B2", 0, "daily_new_word_goal") }

    private fun invalid(level: String?, goal: Int, reason: String) {
        account(21, level, goal)
        val error = assertThrows(FlywayException::class.java) { flyway("9").migrate() }
        assertTrue(generateSequence<Throwable>(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }.contains(reason))
        assertNull(jdbc.queryForObject("SELECT to_regclass('public.learner_settings')", String::class.java))
        assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND type='SQL'", Int::class.java))
        assertEquals(goal, jdbc.queryForObject("SELECT daily_goal FROM users WHERE id=21", Int::class.java))
    }

    private fun account(id: Long, level: String?, goal: Int) {
        jdbc.update("INSERT INTO users(id,email,password,name,target_level,daily_goal) VALUES (?,?,?, ?,?,?)", id, "fixture-$id@example.invalid", "unchanged-password-hash", "Fixture", level, goal)
    }

    companion object { @Container @JvmField val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine") }
}
