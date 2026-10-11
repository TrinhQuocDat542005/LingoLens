package com.quocdat.lingolens.sync

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.common.ApiResponse
import com.quocdat.lingolens.recognition.*
import com.quocdat.lingolens.user.UserRepository
import jakarta.validation.Valid
import jakarta.validation.Validator
import jakarta.validation.constraints.*
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

data class SyncOperation(@field:Positive val accountId: Long, @field:NotBlank val operationId: String,
    @field:NotBlank val kind: String, val payload: JsonNode)
data class SyncReceipt(val operationId: String, val remoteId: Long? = null)
data class CloudEntry(val word: String, val content: String?, val savedAt: Long?, val firstLearnedAt: Long?, val deleted: Boolean)

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(private val service: SyncService) {
    @PostMapping("/operations")
    fun apply(@Valid @RequestBody request: SyncOperation, auth: Authentication) =
        ApiResponse.success("Operation acknowledged", service.apply(request, auth.name))
    @GetMapping("/learning")
    fun learning(@RequestParam accountId: Long, auth: Authentication) =
        ApiResponse.success("Learning snapshot loaded", service.snapshot(accountId, auth.name))
    @GetMapping("/reviews")
    fun reviews(@RequestParam accountId: Long, auth: Authentication) =
        ApiResponse.success("Review snapshot loaded", service.reviews(accountId, auth.name))
}

@Service
class SyncService(private val jdbc: JdbcTemplate, private val users: UserRepository,
    private val recognition: RecognitionService, private val mapper: ObjectMapper, private val validator: Validator,
    private val reviewStore: ReviewStore) {
    private fun account(accountId: Long, email: String): Long {
        val id = users.findByEmail(email).orElseThrow().id!!
        if (id != accountId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Sync account does not match session")
        return id
    }
    @Transactional
    fun apply(request: SyncOperation, email: String): SyncReceipt {
        val id = account(request.accountId, email)
        require(request.operationId.length == 36 && UUID.fromString(request.operationId).toString() == request.operationId) { "Invalid operation ID" }
        require(request.payload.toString().length <= 20000) { "Operation payload too large" }
        // Serialize writes for this account: receipt and effect commit together, including concurrent retries.
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long::class.java, id)
        val hash = MessageDigest.getInstance("SHA-256").digest((request.kind + ":" + canonical(request.payload)).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val existing = jdbc.query("SELECT request_hash, remote_id FROM sync_receipts WHERE user_id = ? AND operation_id = ?",
            { rs, _ -> rs.getString(1) to (rs.getObject(2) as? Number)?.toLong() }, id, request.operationId).firstOrNull()
        if (existing != null) {
            if (existing.first != hash) throw ResponseStatusException(HttpStatus.CONFLICT, "Operation ID reused with different content")
            return SyncReceipt(request.operationId, existing.second)
        }
        val p = request.payload
        val remoteId: Long? = when (request.kind) {
            "SAVE_WORD", "DELETE_WORD", "RECORD_LEARNING" -> {
                val word = p.path("word").asText().trim().lowercase(Locale.ROOT)
                require(word.isNotBlank() && word.length <= 100) { "Invalid word" }
                val first = p.get("firstLearnedAt")?.takeUnless { it.isNull }?.asLong()?.also(::validTime)
                val row = jdbc.query("SELECT first_learned_at FROM learning_entries WHERE user_id = ? AND word = ?",
                    { rs, _ -> (rs.getObject(1) as? Number)?.toLong() }, id, word)
                val earliest = listOfNotNull(row.firstOrNull(), first).minOrNull()
                if (row.isEmpty()) jdbc.update("INSERT INTO learning_entries(user_id, word, deleted) VALUES (?, ?, TRUE)", id, word)
                when (request.kind) {
                    "SAVE_WORD" -> {
                        val content = p.path("content")
                        require(content.isObject && content.path("word").asText().trim().lowercase(Locale.ROOT) == word) { "Invalid vocabulary snapshot" }
                        listOf("translation", "partOfSpeech", "definition", "level", "exampleSentence", "exampleSentenceB2").forEach {
                            require(content.path(it).isTextual && content.path(it).asText().length <= 5000) { "Invalid snapshot field: $it" }
                        }
                        require(content.path("synonyms").isArray && content.path("synonyms").size() <= 50 && content.path("synonyms").all { it.isTextual && it.asText().length <= 100 }) { "Invalid synonyms" }
                        require(content.path("phonetic").isMissingNode || content.path("phonetic").isNull || content.path("phonetic").isTextual) { "Invalid phonetic" }
                        val savedAt = p.path("savedAt").asLong().also(::validTime)
                        jdbc.update("UPDATE learning_entries SET content = ?, saved_at = ?, first_learned_at = ?, deleted = FALSE WHERE user_id = ? AND word = ?",
                            content.toString(), savedAt, earliest ?: savedAt, id, word)
                        reviewStore.initialize(id, word, savedAt)
                    }
                    "DELETE_WORD" -> jdbc.update("UPDATE learning_entries SET deleted = TRUE WHERE user_id = ? AND word = ?", id, word)
                    else -> { require(first != null) { "Missing first learning time" }
                        jdbc.update("UPDATE learning_entries SET first_learned_at = ? WHERE user_id = ? AND word = ?", earliest, id, word) }
                }
                null
            }
            "REVIEW_WORD" -> {
                val word = p.path("word").asText().trim().lowercase(Locale.ROOT)
                require(word.isNotBlank() && word.length <= 100) { "Invalid word" }
                require(p.path("reviewedAt").isIntegralNumber) { "Invalid review time" }
                val time = p.path("reviewedAt").asLong().also(::validTime)
                val grade = com.quocdat.lingolens.review.core.ReviewGrade.valueOf(p.path("grade").asText())
                reviewStore.review(id, request.operationId, word, grade, time)
                null
            }
            "RECOGNITION" -> {
                val body = mapper.treeToValue(p, CreateRecognitionRequest::class.java)
                validate(body); recognition.create(body, email).id
            }
            "REPORT" -> {
                val parent = p.path("recognitionOperationId").asText()
                val historyId = jdbc.query("SELECT remote_id FROM sync_receipts WHERE user_id = ? AND operation_id = ? AND operation_kind = 'RECOGNITION'",
                    { rs, _ -> (rs.getObject(1) as? Number)?.toLong() }, id, parent).firstOrNull()
                    ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Recognition must be synchronized first")
                val body = CreateRecognitionReportRequest(p.path("expectedLabel").asText(), p.get("note")?.takeUnless { it.isNull }?.asText())
                validate(body); recognition.report(historyId, body, email).id
            }
            else -> throw IllegalArgumentException("Unsupported operation kind")
        }
        jdbc.update("INSERT INTO sync_receipts(user_id, operation_id, request_hash, operation_kind, remote_id) VALUES (?, ?, ?, ?, ?)", id, request.operationId, hash, request.kind, remoteId)
        return SyncReceipt(request.operationId, remoteId)
    }
    @Transactional(readOnly = true)
    fun reviews(accountId: Long, email: String): ReviewSnapshot = reviewStore.snapshot(account(accountId, email))
    @Transactional(readOnly = true)
    fun snapshot(accountId: Long, email: String): List<CloudEntry> {
        val id = account(accountId, email)
        return jdbc.query("SELECT word, content, saved_at, first_learned_at, deleted FROM learning_entries WHERE user_id = ? ORDER BY word",
            { rs, _ -> CloudEntry(rs.getString(1), rs.getString(2), (rs.getObject(3) as? Number)?.toLong(), (rs.getObject(4) as? Number)?.toLong(), rs.getBoolean(5)) }, id)
    }
    private fun validTime(time: Long) { require(time > 0 && time <= System.currentTimeMillis() + 86400000) { "Invalid learning time" } }
    private fun <T> validate(body: T) { require(validator.validate(body).isEmpty()) { "Invalid operation payload" } }
    private fun canonical(node: JsonNode): String = when {
        node.isObject -> node.fieldNames().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { mapper.writeValueAsString(it) + ":" + canonical(node[it]) }
        node.isArray -> node.joinToString(prefix = "[", postfix = "]") { canonical(it) }
        else -> node.toString()
    }
}
