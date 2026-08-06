package com.quocdat.lingolens.recognition

import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.auth.LoginRequest
import com.quocdat.lingolens.auth.RegisterRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecognitionControllerTests {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    private fun token(): String {
        val email = "recognition-${UUID.randomUUID()}@lingolens.dev"
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RegisterRequest(email, "Secure123!", "Camera User"))))
            .andExpect(status().isCreated)
        val body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(LoginRequest(email, "Secure123!"))))
            .andExpect(status().isOk).andReturn().response.contentAsString
        return mapper.readTree(body).path("data").path("accessToken").asText()
    }

    @Test
    fun `user can save list and report own recognition once`() {
        val token = token()
        val created = mockMvc.perform(post("/api/v1/recognitions").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"detectedLabel":"Dog","confidence":0.82,"engine":"EFFICIENTDET"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.detectedLabel").value("dog"))
            .andReturn().response.contentAsString
        val id = mapper.readTree(created).path("data").path("id").asLong()

        mockMvc.perform(get("/api/v1/recognitions").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].reported").value(false))

        val reportBody = """{"expectedLabel":"cat","note":"The object was a cat"}"""
        mockMvc.perform(post("/api/v1/recognitions/$id/reports").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(reportBody))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.actualLabel").value("dog"))
            .andExpect(jsonPath("$.data.expectedLabel").value("cat"))

        mockMvc.perform(post("/api/v1/recognitions/$id/reports").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(reportBody))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `recognition endpoints require authentication and validate confidence`() {
        mockMvc.perform(get("/api/v1/recognitions")).andExpect(status().isUnauthorized)
        val token = token()
        mockMvc.perform(post("/api/v1/recognitions").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"detectedLabel":"cat","confidence":1.5,"engine":"AI"}"""))
            .andExpect(status().isBadRequest)
    }
}
