package com.quocdat.lingolens.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.curriculum.ContentSeedImporter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.test.context.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.*
import java.util.UUID

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @Tag("postgres")
class IdentityBoundAuthPostgresTests {
    @Autowired lateinit var mvc:MockMvc
    @Autowired lateinit var jdbc:JdbcTemplate
    @Autowired lateinit var mapper:ObjectMapper
    private fun register(email:String):Long {
        val body=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RegisterRequest(email,"Secure123!","Owner"))))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        return mapper.readTree(body)["data"]["id"].asLong()
    }
    private fun login(email:String):JsonNode=mapper.readTree(mvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(LoginRequest(email,"Secure123!"))))
        .andExpect(status().isOk).andReturn().response.contentAsString)["data"]
    @Test fun `deleted identity old access and refresh cannot authenticate a reregistered email or replay queue`() {
        jdbc.dataSource!!.connection.use{assertEquals("PostgreSQL",it.metaData.databaseProductName);assertTrue(it.metaData.url.startsWith("jdbc:postgresql:"))}
        val email="recreated-${UUID.randomUUID()}@example.invalid";val oldId=register(email);val old=login(email)
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer ${old["accessToken"].asText()}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.id").value(oldId))
        // Fixture physical deletion only: production deletion API is week9 work.
        TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)).executeWithoutResult {
            jdbc.update("DELETE FROM learner_settings WHERE user_id=?",oldId)
            assertEquals(1,jdbc.update("DELETE FROM users WHERE id=?",oldId))
        }
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer ${old["accessToken"].asText()}"))
            .andExpect(status().isUnauthorized)
        val newId=register(email);assertNotEquals(oldId,newId);val fresh=login(email)
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer ${old["accessToken"].asText()}"))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RefreshTokenRequest(old["refreshToken"].asText()))))
            .andExpect(status().isUnauthorized)
        val queued=mapper.writeValueAsString(mapOf("accountId" to oldId,"operationId" to UUID.randomUUID().toString(),"kind" to "SAVE_WORD",
            "payload" to mapOf("word" to "book","content" to emptyMap<String,String>(),"savedAt" to 1000,"firstLearnedAt" to 1000)))
        mvc.perform(post("/api/v1/sync/operations").header("Authorization","Bearer ${old["accessToken"].asText()}")
            .contentType(MediaType.APPLICATION_JSON).content(queued)).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/sync/operations").header("Authorization","Bearer ${fresh["accessToken"].asText()}")
            .contentType(MediaType.APPLICATION_JSON).content(queued)).andExpect(status().isForbidden)
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM learning_entries WHERE user_id IN (?,?)",Int::class.java,oldId,newId))
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM sync_receipts WHERE user_id IN (?,?)",Int::class.java,oldId,newId))
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer ${fresh["accessToken"].asText()}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.id").value(newId))
    }
    @Test fun `identity bound login still reaches authenticated published and archived pack endpoints`() {
        ContentSeedImporter(jdbc,mapper,TransactionTemplate(DataSourceTransactionManager(jdbc.dataSource!!)))
            .import(requireNotNull(javaClass.getResource("/content/demo-week5.json")).readBytes(),setOf("test"))
        val email="pack-owner-${UUID.randomUUID()}@example.invalid";register(email);val tokens=login(email)
        val revision=UUID.fromString("00000000-0000-4000-8000-000000000020")
        val endpoint="/api/v1/curriculum/revisions/$revision/pack"
        val before=mvc.perform(get(endpoint).header("Authorization","Bearer ${tokens["accessToken"].asText()}"))
            .andExpect(status().isOk).andReturn().response.contentAsByteArray
        jdbc.update("UPDATE lesson_revisions SET status='ARCHIVED' WHERE id=?",revision)
        try {assertArrayEquals(before,mvc.perform(get(endpoint).header("Authorization","Bearer ${tokens["accessToken"].asText()}"))
            .andExpect(status().isOk).andReturn().response.contentAsByteArray)}
        finally {jdbc.update("UPDATE lesson_revisions SET status='PUBLISHED' WHERE id=?",revision)}
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        @DynamicPropertySource @JvmStatic fun database(registry:DynamicPropertyRegistry){
            registry.add("spring.datasource.url",postgres::getJdbcUrl);registry.add("spring.datasource.username",postgres::getUsername)
            registry.add("spring.datasource.password",postgres::getPassword);registry.add("spring.datasource.driver-class-name"){"org.postgresql.Driver"}
        }
    }
}
