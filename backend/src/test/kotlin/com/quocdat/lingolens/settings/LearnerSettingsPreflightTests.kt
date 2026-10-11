package com.quocdat.lingolens.settings

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

@Testcontainers
@Tag("postgres")
class LearnerSettingsPreflightTests {
    private lateinit var connection: Connection

    @BeforeEach
    fun fixtureAtV8() {
        // This container belongs only to this suite, never an operational database.
        val flyway = Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .target("8").cleanDisabled(false).load()
        flyway.clean()
        flyway.migrate()
        connection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @AfterEach
    fun close() { if (::connection.isInitialized) connection.close() }

    @Test
    fun `valid A1 B1 B2 and distinct goals produce no findings or mutations`() {
        account(101, "A1", 1)
        account(102, "B1", 8)
        account(103, "B2", 50)
        legacyData(102)
        val before = snapshot()
        assertEquals(emptyList<Map<String, String?>>(), preflight())
        assertEquals(before, snapshot())
        assertEquals(listOf("A1:1", "B1:8", "B2:50"),
            queryStrings("SELECT target_level || ':' || daily_goal FROM users ORDER BY id"))
    }

    @Test
    fun `invalid rows report exact ids values and reasons without identity secrets`() {
        account(201, "C1", 0)
        account(202, null, 51)
        account(203, "B2", -1)
        account(204, "b1", 5)
        account(205, " A1", 5)
        account(206, "B1", 10)
        legacyData(201)
        val before = snapshot()
        val findings = preflight()
        assertEquals(listOf("201", "202", "203", "204", "205"), findings.map { it["user_id"] })
        assertEquals(listOf("TARGET_LEVEL_UNSUPPORTED; DAILY_GOAL_OUT_OF_RANGE",
            "TARGET_LEVEL_NULL; DAILY_GOAL_OUT_OF_RANGE", "DAILY_GOAL_OUT_OF_RANGE",
            "TARGET_LEVEL_UNSUPPORTED", "TARGET_LEVEL_UNSUPPORTED"), findings.map { it["errors"] })
        assertEquals(setOf("user_id", "target_level", "daily_goal", "errors"), findings.first().keys)
        assertEquals("C1", findings.first()["target_level"])
        assertNull(findings[1]["target_level"])
        assertEquals("0", findings.first()["daily_goal"])
        assertEquals(before, snapshot())
        println("PREFLIGHT_INVALID_FIXTURE=$findings")
    }

    @Test
    fun `null goal is reported even in a database with weakened legacy constraints`() {
        // V8 daily_goal is NOT NULL; simulate drift, not a valid V8 production state.
        execute("ALTER TABLE users ALTER COLUMN daily_goal DROP NOT NULL")
        account(301, "B1", null)
        val before = snapshot()
        assertEquals("DAILY_GOAL_NULL", preflight().single()["errors"])
        assertEquals(before, snapshot())
    }

    @Test
    fun `preflight opens a read only transaction enforced by PostgreSQL`() {
        account(401, "B2", 12)
        connection.createStatement().use { it.execute(statements().first()) }
        try {
            assertEquals(listOf("on"), queryStrings("SHOW transaction_read_only"))
            val error = assertThrows(SQLException::class.java) {
                execute("UPDATE users SET daily_goal = 1 WHERE id = 401")
            }
            assertEquals("25006", error.sqlState)
        } finally { execute("ROLLBACK") }
        assertEquals(listOf("12"), queryStrings("SELECT daily_goal FROM users WHERE id = 401"))
    }

    private fun statements(): List<String> {
        val resource = requireNotNull(javaClass.getResource("/preflight/learner-settings-v9.sql")) {
            "Missing read-only preflight SQL"
        }
        // Statement terminators are at line ends; '; ' inside concat_ws is data.
        return resource.readText().split(Regex(";\\s*(?:\\r?\\n|$)"))
            .map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun preflight(): List<Map<String, String?>> {
        val findings = mutableListOf<Map<String, String?>>()
        try {
            statements().forEach { sql ->
                connection.createStatement().use { statement ->
                    if (statement.execute(sql)) statement.resultSet.use { result ->
                        while (result.next()) {
                            findings += (1..result.metaData.columnCount).associate {
                                result.metaData.getColumnLabel(it) to result.getString(it)
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            execute("ROLLBACK")
            throw error
        }
        return findings
    }

    private fun account(id: Long, level: String?, goal: Int?) {
        connection.prepareStatement("INSERT INTO users(id,email,password,name,target_level,daily_goal) VALUES (?, ?, ?, ?, ?, ?)").use {
            it.setLong(1, id); it.setString(2, "fixture-$id@example.invalid")
            it.setString(3, "fixture-password-hash"); it.setString(4, "Private fixture name")
            it.setString(5, level); it.setObject(6, goal); it.executeUpdate()
        }
    }

    private fun legacyData(id: Long) {
        execute("INSERT INTO user_roles SELECT $id, id FROM roles")
        execute("INSERT INTO refresh_tokens(token_hash,user_id,expiry_date) VALUES ('fixture-token-hash',$id,'2030-01-01')")
        execute("INSERT INTO learning_entries VALUES ($id,'book','{}',1700000000000,1700000000000,FALSE)")
        execute("INSERT INTO learning_entries VALUES ($id,'dog',NULL,NULL,NULL,TRUE)")
        execute("INSERT INTO review_cards(user_id,word,due_at,interval_days,repetitions) VALUES ($id,'book',1700086400000,1,1)")
        execute("INSERT INTO review_events VALUES ($id,'00000000-0000-0000-0000-000000000001','book','GOOD',1700000000000)")
        execute("INSERT INTO sync_receipts(user_id,operation_id,request_hash,operation_kind) VALUES ($id,'00000000-0000-0000-0000-000000000001','fixture-hash','REVIEW_WORD')")
    }

    private fun snapshot(): Map<String, List<String>> = queryStrings(
        "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename"
    ).associateWith { table -> queryStrings("SELECT row_to_json(t)::text FROM public.\"$table\" t ORDER BY row_to_json(t)::text") }

    private fun queryStrings(sql: String): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { result -> buildList { while (result.next()) add(result.getString(1)) } }
    }

    private fun execute(sql: String) { connection.createStatement().use { it.execute(sql) } }

    companion object {
        @Container @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine")
    }
}
