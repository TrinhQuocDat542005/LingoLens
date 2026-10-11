package com.quocdat.lingolens.vocabulary

import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.admin.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VocabularyControllerTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var repository: VocabularyRepository
    private fun token(): String {
        val email = "vocab-${UUID.randomUUID()}@example.com"
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"Secure123!","name":"Learner"}"""))
            .andExpect(status().isCreated)
        val response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"Secure123!"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        return mapper.readTree(response).path("data").path("accessToken").asText()
    }
    @Test fun `published catalog requires authentication`() {
        mvc.perform(get("/api/v1/vocabularies")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/vocabularies/book")).andExpect(status().isUnauthorized)
    }
    @Test fun `drafts are hidden and lookup is case insensitive`() {
        val access = token()
        repository.saveAndFlush(Vocabulary(word = "portfolio-public", translation = "công khai", definition = "Published content", phonetic = "/test/", synonyms = "one, two", published = true))
        val draft = repository.saveAndFlush(Vocabulary(word = "portfolio-draft", translation = "bản nháp", definition = "Private content", published = false))
        mvc.perform(get("/api/v1/vocabularies/PORTFOLIO-PUBLIC").header("Authorization", "Bearer $access"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.definition").value("Published content"))
            .andExpect(jsonPath("$.data.synonyms[1]").value("two"))
        mvc.perform(get("/api/v1/vocabularies").param("search", "portfolio").header("Authorization", "Bearer $access"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.content.length()").value(1))
        mvc.perform(get("/api/v1/vocabularies/portfolio-draft").header("Authorization", "Bearer $access"))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("VOCABULARY_NOT_FOUND"))
        draft.published = true; repository.saveAndFlush(draft)
        mvc.perform(get("/api/v1/vocabularies/portfolio-draft").header("Authorization", "Bearer $access"))
            .andExpect(status().isOk)
        draft.published = false; repository.saveAndFlush(draft)
        mvc.perform(get("/api/v1/vocabularies/portfolio-draft").header("Authorization", "Bearer $access"))
            .andExpect(status().isNotFound)
        mvc.perform(get("/api/v1/vocabularies/missing-word").header("Authorization", "Bearer $access"))
            .andExpect(status().isNotFound)
    }
    @Test fun `pagination normalizes invalid bounds and filters translations`() {
        val access = token()
        repository.saveAndFlush(Vocabulary(word = "portfolio-paging", translation = "unique-translation", definition = "Test"))
        mvc.perform(get("/api/v1/vocabularies").param("search", "unique-translation").param("page", "-1").param("size", "0")
            .header("Authorization", "Bearer $access"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.content[0].word").value("portfolio-paging"))
            .andExpect(jsonPath("$.data.totalPages").value(1))
        mvc.perform(get("/api/v1/vocabularies").param("page", "999").param("size", "1000000").header("Authorization", "Bearer $access"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.content.length()").value(0))
    }
}
