package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers @Tag("postgres")
class ContentSeedTests {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var importer: ContentSeedImporter
    private val bytes get() = requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes()
    @BeforeEach fun database() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val flyway = Flyway.configure().dataSource(source).cleanDisabled(false).load()
        flyway.clean(); flyway.migrate()
        jdbc = JdbcTemplate(source)
        importer = ContentSeedImporter(jdbc, jacksonObjectMapper(), TransactionTemplate(DataSourceTransactionManager(source)))
    }
    @Test fun `demo import is explicit atomic and checksum stable`() {
        val legacy = jdbc.queryForList("SELECT row_to_json(v)::text FROM vocabularies v ORDER BY id", String::class.java)
        assertEquals(0, count("courses"))
        assertTrue(importer.import(bytes, setOf("dev")))
        assertFalse(importer.import(bytes, setOf("test")))
        assertEquals(1, count("content_import_receipts")); assertEquals(3, count("lessons"))
        assertEquals(24, count("questions")); assertEquals(18, count("lesson_vocabularies"))
        assertEquals(legacy, jdbc.queryForList("SELECT row_to_json(v)::text FROM vocabularies v WHERE id<=12 ORDER BY id", String::class.java))
        assertThrows(IllegalArgumentException::class.java) { importer.import(bytes + byteArrayOf(32), setOf("dev")) }
        assertEquals(24, count("questions"))
    }
    @Test fun `concurrent same pack imports have one effect`() {
        val executor = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
        try {
            val jobs = (1..2).map { executor.submit<Boolean> { start.await(); importer.import(bytes, setOf("dev")) } }
            start.countDown()
            assertEquals(listOf(false, true), jobs.map { it.get(20, TimeUnit.SECONDS) }.sorted())
            assertEquals(1, count("content_import_receipts")); assertEquals(24, count("questions"))
        } finally { executor.shutdownNow() }
    }
    @Test fun `invalid seed fails before any write and collision rolls back`() {
        val mapper = jacksonObjectMapper()
        val bad = mapper.readTree(bytes) as com.fasterxml.jackson.databind.node.ObjectNode
        bad.put("unknown", true)
        assertThrows(IllegalArgumentException::class.java) { importer.import(mapper.writeValueAsBytes(bad), setOf("dev")) }
        assertEquals(0, count("courses"))
        jdbc.update("INSERT INTO vocabularies(word,translation,definition,level) VALUES ('hello','incompatible','Wrong','B2')")
        assertThrows(IllegalArgumentException::class.java) { importer.import(bytes, setOf("dev")) }
        assertEquals(0, count("courses")); assertEquals(0, count("content_import_receipts"))
        assertEquals(13, count("vocabularies"))
    }
    @Test fun `production refuses seed and guard detects orphan demo content`() {
        assertThrows(IllegalArgumentException::class.java) { importer.import(bytes, setOf("dev", "prod")) }
        assertThrows(IllegalArgumentException::class.java) { importer.import(bytes, setOf("production")) }
        importer.import(bytes, setOf("dev"))
        assertThrows(IllegalStateException::class.java) { ProductionContentGuard.verify(jdbc, setOf("prod")) }
        jdbc.update("DELETE FROM content_import_receipts")
        assertThrows(IllegalStateException::class.java) { ProductionContentGuard.verify(jdbc, setOf("production", "test")) }
        ProductionContentGuard.verify(jdbc, setOf("dev"))
    }
    @Test fun `production guard fails bean initialization before serving requests`() {
        importer.import(bytes, setOf("dev"))
        val context = org.springframework.context.support.GenericApplicationContext()
        context.environment.setActiveProfiles("prod", "dev")
        context.registerBean(ProductionContentGuard::class.java, java.util.function.Supplier { ProductionContentGuard(jdbc, context.environment) })
        try {
            val error = assertThrows(org.springframework.beans.BeansException::class.java) { context.refresh() }
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it.message.orEmpty().contains("Production startup refused") })
            assertTrue(!context.isActive)
        } finally { context.close() }
    }
    @Test fun `semantic seed errors are rejected before writes`() {
        val mapper = jacksonObjectMapper()
        val mutations: List<(com.fasterxml.jackson.databind.node.ObjectNode) -> Unit> = listOf(
            { (it["unit"] as com.fasterxml.jackson.databind.node.ObjectNode).put("courseId", java.util.UUID.randomUUID().toString()) },
            { (it["lessons"][0]["questions"] as com.fasterxml.jackson.databind.node.ArrayNode).remove(0) },
            { (it["lessons"][0]["questions"][0]["answerKey"] as com.fasterxml.jackson.databind.node.ObjectNode).put("optionKey", "missing") },
            { (it["lessons"][0]["questions"][0] as com.fasterxml.jackson.databind.node.ObjectNode).put("weight", 0) },
            { (it["lessons"][0] as com.fasterxml.jackson.databind.node.ObjectNode).put("id", it["lessons"][1]["id"].asText()) },
            { it.put("pronunciationLocale", "en-GB") }
        )
        mutations.forEach { mutate ->
            val pack = mapper.readTree(bytes) as com.fasterxml.jackson.databind.node.ObjectNode
            mutate(pack)
            assertThrows(IllegalArgumentException::class.java) { importer.import(mapper.writeValueAsBytes(pack), setOf("dev")) }
            assertEquals(0, count("courses")); assertEquals(0, count("content_import_receipts"))
        }
    }
    @Test fun `partial indexes enforce at most one published revision and active enrollment`() {
        importer.import(bytes, setOf("dev"))
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname IN ('uq_lesson_published_revision','uq_user_active_enrollment') AND indexdef LIKE '%WHERE%'", Int::class.java)!!)
        val lesson = jdbc.queryForObject("SELECT id FROM lessons ORDER BY position LIMIT 1", java.util.UUID::class.java)!!
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            jdbc.update("INSERT INTO lesson_revisions(id,lesson_id,revision_no,introduction,sentence_patterns,status,content_status,pack_id) VALUES (?, ?,2,'x','[]','PUBLISHED','DEMO',?)", java.util.UUID.randomUUID(), lesson, packId())
        }
        jdbc.update("INSERT INTO users(id,email,password,name) VALUES (99,'fixture@example.invalid','hash','Fixture')")
        val course = jdbc.queryForObject("SELECT id FROM courses", java.util.UUID::class.java)!!
        jdbc.update("INSERT INTO enrollments(user_id,course_id,status,enrolled_at) VALUES (99,?,'ACTIVE',CURRENT_TIMESTAMP)", course)
        val other = java.util.UUID.randomUUID()
        jdbc.update("INSERT INTO courses(id,code,title,description,level,status,created_at,content_status,pack_id) VALUES (?,'other','Other','Other','A1','DRAFT',CURRENT_TIMESTAMP,'DEMO',?)", other, packId())
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            jdbc.update("INSERT INTO enrollments(user_id,course_id,status,enrolled_at) VALUES (99,?,'ACTIVE',CURRENT_TIMESTAMP)", other)
        }
        jdbc.update("INSERT INTO enrollments(user_id,course_id,status,enrolled_at) VALUES (99,?,'PAUSED',CURRENT_TIMESTAMP)", other)
    }
    @Test fun `published revision snapshots questions and options are immutable`() {
        importer.import(bytes, setOf("dev"))
        listOf("UPDATE lesson_revisions SET introduction='tampered'", "UPDATE lesson_vocabularies SET content_snapshot='{}'",
            "UPDATE questions SET prompt='tampered'", "UPDATE question_options SET label='tampered'", "DELETE FROM lesson_revisions").forEach {
            assertThrows(org.springframework.dao.DataAccessException::class.java) { jdbc.update(it) }
        }
        jdbc.update("UPDATE lesson_revisions SET status='ARCHIVED'")
        assertThrows(org.springframework.dao.DataAccessException::class.java) { jdbc.update("UPDATE questions SET prompt='tampered'") }
    }
    private fun count(table: String) = jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!
    private fun packId() = jdbc.queryForObject("SELECT pack_id FROM courses LIMIT 1", java.util.UUID::class.java)!!
    companion object { @Container @JvmField val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine") }
}
