package com.quocdat.lingolens.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.auth.LoginRequest
import com.quocdat.lingolens.auth.RegisterRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("postgres")
@Transactional
class PostgresIntegrationTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var sync: com.quocdat.lingolens.sync.SyncService

    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `concurrent review retries advance PostgreSQL schedule once`() {
        val email = "review-${UUID.randomUUID()}@lingolens.dev"
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RegisterRequest(email, "Secure123!", "Review User")))).andExpect(status().isCreated)
        val account = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)!!
        val time = System.currentTimeMillis()
        val content = """{"word":"book","translation":"sách","partOfSpeech":"Noun","definition":"A book","level":"A1","synonyms":[],"exampleSentence":"Read.","exampleSentenceB2":""}"""
        sync.apply(com.quocdat.lingolens.sync.SyncOperation(account, UUID.randomUUID().toString(), "SAVE_WORD",
            mapper.readTree("""{"word":"book","content":$content,"savedAt":$time,"firstLearnedAt":$time}""")), email)
        val operation = com.quocdat.lingolens.sync.SyncOperation(account, UUID.randomUUID().toString(), "REVIEW_WORD",
            mapper.readTree("""{"word":"book","grade":"GOOD","reviewedAt":$time}"""))
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val requests = (1..2).map { executor.submit<String> { start.await(); sync.apply(operation, email).operationId } }
            start.countDown()
            requests.forEach { assertEquals(operation.operationId, it.get(30, java.util.concurrent.TimeUnit.SECONDS)) }
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review_events WHERE user_id = ?", Int::class.java, account)!!)
            assertEquals(1, jdbc.queryForObject("SELECT repetitions FROM review_cards WHERE user_id = ?", Int::class.java, account)!!)
            assertEquals(time + 86400000L, jdbc.queryForObject("SELECT due_at FROM review_cards WHERE user_id = ?", Long::class.java, account))
        } finally { executor.shutdownNow() }
    }

    @Test fun `all migrations apply and seed vocabulary on PostgreSQL`() {
        assertEquals(8, jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND type = 'SQL'", Int::class.java)!!)
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM roles", Int::class.java)!!)
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies WHERE is_published = TRUE", Int::class.java)!! > 0)
        assertEquals("PostgreSQL", jdbc.dataSource!!.connection.use { it.metaData.databaseProductName })
    }

    @Test fun `recognition ownership and duplicate reports work on PostgreSQL`() {
        fun accessToken(): String {
            val email = "postgres-${UUID.randomUUID()}@lingolens.dev"
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(RegisterRequest(email, "Secure123!", "Postgres User"))))
                .andExpect(status().isCreated)
            val body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(LoginRequest(email, "Secure123!"))))
                .andExpect(status().isOk).andReturn().response.contentAsString
            return mapper.readTree(body).path("data").path("accessToken").asText()
        }
        val owner = accessToken()
        val other = accessToken()
        val created = mockMvc.perform(post("/api/v1/recognitions").header("Authorization", "Bearer $owner")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"detectedLabel":"dog","confidence":0.82,"engine":"EFFICIENTDET"}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = mapper.readTree(created).path("data").path("id").asLong()
        mockMvc.perform(get("/api/v1/recognitions").header("Authorization", "Bearer $other"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(0))
        val report = """{"expectedLabel":"cat","note":"Ownership check"}"""
        mockMvc.perform(post("/api/v1/recognitions/$id/reports").header("Authorization", "Bearer $other")
            .contentType(MediaType.APPLICATION_JSON).content(report)).andExpect(status().isBadRequest)
        mockMvc.perform(post("/api/v1/recognitions/$id/reports").header("Authorization", "Bearer $owner")
            .contentType(MediaType.APPLICATION_JSON).content(report)).andExpect(status().isCreated)
        mockMvc.perform(post("/api/v1/recognitions/$id/reports").header("Authorization", "Bearer $owner")
            .contentType(MediaType.APPLICATION_JSON).content(report)).andExpect(status().isBadRequest)
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM recognition_reports WHERE recognition_history_id = ?", Int::class.java, id)!!)
    }

    @Test fun `published vocabulary lookup and search work on PostgreSQL`() {
        val email = "catalog-${UUID.randomUUID()}@lingolens.dev"
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RegisterRequest(email, "Secure123!", "Catalog User"))))
            .andExpect(status().isCreated)
        val response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(LoginRequest(email, "Secure123!"))))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val access = mapper.readTree(response).path("data").path("accessToken").asText()
        mockMvc.perform(get("/api/v1/vocabularies/BOOK").header("Authorization", "Bearer $access"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.word").value("book"))
        jdbc.update("UPDATE vocabularies SET is_published = FALSE WHERE word = 'book'")
        mockMvc.perform(get("/api/v1/vocabularies/book").header("Authorization", "Bearer $access"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/vocabularies").param("search", "book").header("Authorization", "Bearer $access"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.content.length()").value(0))
    }

    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `concurrent sync retries commit exactly one recognition on PostgreSQL`() {
        val email = "concurrent-${UUID.randomUUID()}@lingolens.dev"
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RegisterRequest(email, "Secure123!", "Concurrent User"))))
            .andExpect(status().isCreated)
        val response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(LoginRequest(email, "Secure123!"))))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val access = mapper.readTree(response).path("data").path("accessToken").asText()
        val userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)!!
        val id = UUID.randomUUID().toString()
        val body = """{"accountId":$userId,"operationId":"$id","kind":"RECOGNITION","payload":{"detectedLabel":"book","confidence":0.9,"engine":"EFFICIENTDET"}}"""
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val requests = (1..2).map {
                executor.submit<Long> {
                    start.await()
                    val result = mockMvc.perform(post("/api/v1/sync/operations").header("Authorization", "Bearer $access")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk).andReturn().response.contentAsString
                    mapper.readTree(result).path("data").path("remoteId").asLong()
                }
            }
            start.countDown()
            val ids = requests.map { it.get(30, java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(ids.first(), ids.last())
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recognition_history WHERE user_id = ?", Int::class.java, userId)!!)
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sync_receipts WHERE user_id = ?", Int::class.java, userId)!!)
        } finally { executor.shutdownNow() }
    }

    companion object {
        @Container @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine")

        @DynamicPropertySource @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }
}
