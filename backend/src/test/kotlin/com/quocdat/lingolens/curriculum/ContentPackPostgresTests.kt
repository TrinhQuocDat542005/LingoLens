package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.security.MessageDigest
import java.util.UUID

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @Tag("postgres")
class ContentPackPostgresTests {
    @Autowired lateinit var service: ContentPackService
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mockMvc: MockMvc
    private val mapper=jacksonObjectMapper()
    private val course=UUID.fromString("00000000-0000-4000-8000-000000000002")
    private fun seed(){ ContentSeedImporter(jdbc,mapper,TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)))
        .import(requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes(),setOf("test")) }
    @Test fun `manifest has three stable immutable packs including answer keys`() {
        jdbc.dataSource!!.connection.use { connection ->
            assertEquals("PostgreSQL",connection.metaData.databaseProductName)
            assertTrue(connection.metaData.url.startsWith("jdbc:postgresql:"))
        }
        seed(); val manifest=service.manifest(course)
        assertEquals(3,manifest.lessons.size)
        manifest.lessons.forEach { reference ->
            val first=service.pack(UUID.fromString(reference.revisionId)); val second=service.pack(UUID.fromString(reference.revisionId))
            assertArrayEquals(first,second); assertEquals(first.size.toLong(),reference.byteLength)
            assertEquals(MessageDigest.getInstance("SHA-256").digest(first).joinToString("") { "%02x".format(it) },reference.checksum)
            val body=mapper.readTree(first); assertEquals(8,body["questions"].size()); assertEquals(6,body["vocabularies"].size())
            assertEquals("en-US",body["pronunciationLocale"].asText())
            assertTrue(body["questions"].all { it["answerKey"]["optionKey"].asText().isNotEmpty() })
        }
    }
    @Test fun `pack endpoint requires login and unpublished revisions are rejected`() {
        seed(); val published=service.manifest(course).lessons.first().revisionId
        mockMvc.perform(get("/api/v1/curriculum/revisions/$published/pack")).andExpect(status().isUnauthorized)
        val draft=UUID.randomUUID(); val lesson=jdbc.queryForObject("SELECT lesson_id FROM lesson_revisions WHERE id=?",UUID::class.java,UUID.fromString(published))!!
        jdbc.update("INSERT INTO lesson_revisions(id,lesson_id,revision_no,introduction,sentence_patterns,status,content_status,pack_id) SELECT ?,?,99,'draft','[]','DRAFT',content_status,pack_id FROM lesson_revisions WHERE id=?",draft,lesson,UUID.fromString(published))
        assertThrows(com.quocdat.lingolens.assessment.LessonRequestException::class.java){service.pack(draft)}
        val old=service.pack(UUID.fromString(published))
        jdbc.update("UPDATE lesson_revisions SET status='ARCHIVED' WHERE id=?",UUID.fromString(published))
        try { assertArrayEquals(old,service.pack(UUID.fromString(published))) }
        finally { jdbc.update("UPDATE lesson_revisions SET status='PUBLISHED' WHERE id=?",UUID.fromString(published)) }
    }
    @Test fun `large format fixture retains ten questions long labels and six words in each lesson`() {
        val fixture=mapper.readTree(requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json")))
        assertEquals(3,fixture.size())
        fixture.forEach { body ->
            val pack=mapper.treeToValue(body,com.quocdat.lingolens.assessment.core.LessonPack::class.java)
            val roundTrip=mapper.readTree(mapper.writeValueAsBytes(pack))
            assertEquals(body,roundTrip)
            assertEquals(10,pack.questions.size); assertEquals(6,pack.vocabularies.size)
            assertTrue(pack.questions.all { q -> q.options.all { it.label.length>200 } })
        }
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        @DynamicPropertySource @JvmStatic fun database(registry:DynamicPropertyRegistry){
            registry.add("spring.datasource.url",postgres::getJdbcUrl);registry.add("spring.datasource.username",postgres::getUsername);registry.add("spring.datasource.password",postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }
}
