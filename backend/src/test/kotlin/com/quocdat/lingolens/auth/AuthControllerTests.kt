package com.quocdat.lingolens.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.LingoLensApplication
import com.quocdat.lingolens.user.UserRepository
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest(classes = [LingoLensApplication::class])
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    private fun generateRandomEmail(): String {
        return "test_${UUID.randomUUID()}@lingolens.com"
    }

    @Test
    fun testRegisterSuccess() {
        val email = generateRandomEmail()
        val request = RegisterRequest(
            email = email,
            password = "securePassword123",
            name = "Test User"
        )

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message", `is`("User registered successfully with ID: ${userRepository.findByEmail(email).get().id}")))
    }

    @Test
    fun testRegisterDuplicateEmail() {
        val email = generateRandomEmail()
        val request = RegisterRequest(
            email = email,
            password = "securePassword123",
            name = "Test User"
        )

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error", `is`("Email is already registered")))
    }

    @Test
    fun testRegisterWeakPassword() {
        val email = generateRandomEmail()
        val request = RegisterRequest(
            email = email,
            password = "weak",
            name = "Test User"
        )

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.password", `is`("Password must be at least 8 characters")))
    }

    @Test
    fun testLoginSuccessAndGetProfile() {
        val email = generateRandomEmail()
        val password = "securePassword123"
        val request = RegisterRequest(
            email = email,
            password = password,
            name = "Test User"
        )

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)

        val loginRequest = LoginRequest(email = email, password = password)
        val loginResult = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken", notNullValue()))
            .andExpect(jsonPath("$.refreshToken", notNullValue()))
            .andExpect(jsonPath("$.email", `is`(email)))
            .andReturn()

        val responseBody = loginResult.response.contentAsString
        val tokenResponse = objectMapper.readValue(responseBody, TokenResponse::class.java)
        val accessToken = tokenResponse.accessToken

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email", `is`(email)))
            .andExpect(jsonPath("$.name", `is`("Test User")))
    }

    @Test
    fun testLoginWrongCredentials() {
        val loginRequest = LoginRequest(email = "nonexistent@lingolens.com", password = "wrongpassword")
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error", `is`("Unauthorized")))
            .andExpect(jsonPath("$.message", `is`("Invalid email or password")))
    }

    @Test
    fun testGetProfileWithoutToken() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isForbidden)
    }
}
