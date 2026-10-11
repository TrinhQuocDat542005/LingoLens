package com.quocdat.lingolens.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SyncControllerTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
    private fun user(): Pair<Long, String> {
        val email = "sync-${UUID.randomUUID()}@example.com"
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"Secure123!","name":"Sync User"}"""))
            .andExpect(status().isCreated)
        val login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"Secure123!"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)!! to mapper.readTree(login).path("data").path("accessToken").asText()
    }
    private fun send(user: Pair<Long, String>, id: String, kind: String, payload: String) = mvc.perform(
        post("/api/v1/sync/operations").header("Authorization", "Bearer ${user.second}").contentType(MediaType.APPLICATION_JSON)
            .content("""{"accountId":${user.first},"operationId":"$id","kind":"$kind","payload":$payload}"""))
    private val snapshot = """{"id":"book","word":"book","translation":"sách","partOfSpeech":"Noun","definition":"A book","level":"A1","synonyms":[],"exampleSentence":"Read a book.","exampleSentenceB2":""}"""

    @Test fun `learning operations replay exactly once and deletion retains learning history`() {
        val user = user(); val id = UUID.randomUUID().toString(); val time = System.currentTimeMillis()
        val body = """{"word":"book","content":$snapshot,"savedAt":$time,"firstLearnedAt":$time}"""
        repeat(2) { send(user, id, "SAVE_WORD", body).andExpect(status().isOk).andExpect(jsonPath("$.data.operationId").value(id)) }
        send(user, id, "DELETE_WORD", """{"word":"book"}""").andExpect(status().isConflict)
        send(user, UUID.randomUUID().toString(), "DELETE_WORD", """{"word":"book"}""").andExpect(status().isOk)
        send(user, id, "SAVE_WORD", body).andExpect(status().isOk) // Late replay must not resurrect the deleted word.
        mvc.perform(get("/api/v1/sync/learning").param("accountId", user.first.toString()).header("Authorization", "Bearer ${user.second}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data[0].deleted").value(true))
            .andExpect(jsonPath("$.data[0].firstLearnedAt").value(time))
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sync_receipts WHERE user_id = ?", Int::class.java, user.first)!!)
    }
    @Test fun `recognition and dependent report replays do not duplicate effects`() {
        val user = user(); val recognition = UUID.randomUUID().toString(); val report = UUID.randomUUID().toString()
        val body = """{"detectedLabel":"book","confidence":0.8,"engine":"EFFICIENTDET"}"""
        repeat(2) { send(user, recognition, "RECOGNITION", body).andExpect(status().isOk) }
        val correction = """{"recognitionOperationId":"$recognition","expectedLabel":"cup","note":"Offline report"}"""
        repeat(2) { send(user, report, "REPORT", correction).andExpect(status().isOk) }
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recognition_history WHERE user_id = ?", Int::class.java, user.first)!!)
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recognition_reports WHERE user_id = ?", Int::class.java, user.first)!!)
    }
    @Test fun `account fencing rejects mismatched uploads reads and report dependencies`() {
        val owner = user(); val other = user(); val id = UUID.randomUUID().toString()
        val body = """{"detectedLabel":"book","confidence":0.8,"engine":"EFFICIENTDET"}"""
        send(owner, id, "RECOGNITION", body).andExpect(status().isOk)
        send(owner.first to other.second, UUID.randomUUID().toString(), "RECOGNITION", body).andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/sync/learning").param("accountId", owner.first.toString()).header("Authorization", "Bearer ${other.second}"))
            .andExpect(status().isForbidden)
        send(other, UUID.randomUUID().toString(), "REPORT", """{"recognitionOperationId":"$id","expectedLabel":"cup"}""").andExpect(status().isConflict)
    }
    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `invalid operations leave no receipt or partial learning row`() {
        val user = user()
        send(user, UUID.randomUUID().toString(), "SAVE_WORD", """{"word":"book","content":{},"savedAt":1}""").andExpect(status().isBadRequest)
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learning_entries WHERE user_id = ?", Int::class.java, user.first)!!)
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sync_receipts WHERE user_id = ?", Int::class.java, user.first)!!)
        mvc.perform(post("/api/v1/sync/operations").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized)
    }
}
