package com.quocdat.lingolens.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.notNullValue
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
class AuthControllerTests {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    private fun email() = "test-${UUID.randomUUID()}@lingolens.dev"
    private fun register(email: String, password: String = "Secure123!") = mockMvc.perform(
        post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(RegisterRequest(email, password, "Test User")))
    )
    private fun login(email: String, password: String = "Secure123!"): JsonNode {
        val body = mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(LoginRequest(email, password)))
        ).andExpect(status().isOk).andExpect(jsonPath("$.data.accessToken", notNullValue())).andReturn().response.contentAsString
        return mapper.readTree(body).path("data")
    }

    @Test fun `register validates input and rejects duplicate email`() {
        val email = email()
        register(email).andExpect(status().isCreated).andExpect(jsonPath("$.data.email").value(email))
        register(email).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
        register(email(), "short").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0]", containsString("password")))
    }

    @Test fun `login profile update refresh and logout work end to end`() {
        val email = email(); register(email).andExpect(status().isCreated)
        val tokens = login(email)
        val access = tokens.path("accessToken").asText(); val refresh = tokens.path("refreshToken").asText()
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer $access"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.email").value(email))
        mockMvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer $access")
            .contentType(MediaType.APPLICATION_JSON).content("""{"name":"Updated","targetLevel":"B2","dailyGoal":8}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.targetLevel").value("B2"))
        val refreshedBody = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("""{"refreshToken":"$refresh"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val rotated = mapper.readTree(refreshedBody).path("data")
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer ${rotated.path("accessToken").asText()}")
            .contentType(MediaType.APPLICATION_JSON).content("""{"refreshToken":"${rotated.path("refreshToken").asText()}"}"""))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("""{"refreshToken":"${rotated.path("refreshToken").asText()}"}"""))
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
    }

    @Test fun `authentication and role failures use 401 and 403`() {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        val email = email(); register(email); val access = login(email).path("accessToken").asText()
        mockMvc.perform(get("/api/v1/admin/hello").header("Authorization", "Bearer $access"))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }
}
