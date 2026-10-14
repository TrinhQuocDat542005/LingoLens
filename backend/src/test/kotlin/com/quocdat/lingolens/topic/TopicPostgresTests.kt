package com.quocdat.lingolens.topic

import com.quocdat.lingolens.auth.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.*
import java.util.UUID

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @Tag("postgres") @Transactional
class TopicPostgresTests {
    @Autowired lateinit var jdbc:JdbcTemplate
    @Autowired lateinit var auth:AuthService
    @Autowired lateinit var mvc:MockMvc
    @Autowired lateinit var topics:TopicService
    @Test fun `endpoint exposes published public mapping only without changing vocabulary contract`() {
        jdbc.dataSource!!.connection.use{assertEquals("PostgreSQL",it.metaData.databaseProductName)}
        mvc.perform(get("/api/v1/topics")).andExpect(status().isUnauthorized)
        val email="topic-${UUID.randomUUID()}@example.invalid"
        auth.register(RegisterRequest(email,"Secure123!","Topic"))
        val token=auth.login(LoginRequest(email,"Secure123!"),null).accessToken
        mvc.perform(get("/api/v1/topics").header("Authorization","Bearer $token"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data[0].slug").value("objects"))
            .andExpect(jsonPath("$.data[0].words.length()").value(3))
        mvc.perform(get("/api/v1/vocabularies/book").header("Authorization","Bearer $token"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.word").value("book"))
    }
    @Test fun `unpublished vocabulary or topic never appears in mapping`() {
        assertEquals(listOf("book","bottle","phone"),topics.published().single().words)
        jdbc.update("UPDATE vocabularies SET is_published=FALSE WHERE word='phone'")
        assertEquals(listOf("book","bottle"),topics.published().single().words)
        jdbc.update("UPDATE topics SET is_published=FALSE WHERE slug='objects'")
        assertTrue(topics.published().isEmpty())
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        @DynamicPropertySource @JvmStatic fun database(r:DynamicPropertyRegistry){
            r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername)
            r.add("spring.datasource.password",postgres::getPassword);r.add("spring.datasource.driver-class-name"){"org.postgresql.Driver"}
        }
    }
}
