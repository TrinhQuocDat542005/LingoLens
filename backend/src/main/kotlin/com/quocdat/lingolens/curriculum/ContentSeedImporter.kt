package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class ContentSeedImporter(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val transaction: TransactionTemplate) {
    fun import(bytes: ByteArray, profiles: Set<String>): Boolean {
        require(profiles.any { it in setOf("dev", "test") } && profiles.none { it in setOf("prod", "production") }) { "Seed runner is dev/test only" }
        require(bytes.size <= 2_000_000) { "Seed too large" }
        val root = try { mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(bytes).also(SeedValidator::validate) }
            catch (error: Exception) { throw IllegalArgumentException("Invalid seed: ${error.message}", error) }
        require(root["contentStatus"].asText() == "DEMO") { "REVIEWED content requires a reviewed main migration" }
        val pack = uuid(root,"packId"); val checksum = hash(bytes)
        return transaction.execute {
            jdbc.execute("SELECT pg_advisory_xact_lock(${java.nio.ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(pack.toString().toByteArray())).long})")
            val previous = jdbc.queryForList("SELECT checksum FROM content_import_receipts WHERE pack_id=?", String::class.java, pack).firstOrNull()
            if (previous != null) { require(previous == checksum) { "Pack UUID already imported with a different checksum" }; return@execute false }
            val now = java.sql.Timestamp.from(Instant.now())
            jdbc.update("INSERT INTO content_import_receipts VALUES (?,?, 'DEMO',?)", pack, checksum, now)
            val topic = root["topic"]; val course = root["course"]; val unit = root["unit"]
            jdbc.update("INSERT INTO topics VALUES (?,?,?,?,?,?,TRUE,?,?)", uuid(topic), s(topic,"slug"), s(topic,"title"), s(topic,"description"), s(topic,"iconKey"), topic["displayOrder"].asInt(), now, now)
            jdbc.update("INSERT INTO courses(id,code,title,description,level,status,published_at,created_at,content_status,pack_id) VALUES (?,?,?,?,?,'PUBLISHED',?,?,'DEMO',?)", uuid(course),s(course,"code"),s(course,"title"),s(course,"description"),s(course,"level"),now,now,pack)
            jdbc.update("INSERT INTO course_units VALUES (?,?,?,?,?)",uuid(unit),uuid(course),uuid(topic),s(unit,"title"),unit["position"].asInt())
            val vocabulary = root["vocabularies"].associate { word ->
                val existing = jdbc.queryForList("SELECT id,translation,phonetic,part_of_speech,definition,example_sentence,level FROM vocabularies WHERE lower(word)=?", s(word,"word"))
                val id = if (existing.isNotEmpty()) {
                    require(existing.size == 1)
                    val row = existing.single()
                    val expected = mapOf("translation" to s(word,"translation"),"phonetic" to s(word,"phonetic"),"part_of_speech" to s(word,"partOfSpeech"),"definition" to s(word,"definition"),"example_sentence" to s(word,"example"),"level" to s(word,"level"))
                    require(expected.all { (key,value) -> row[key] == value }) { "Incompatible existing word: ${s(word,"word")}" }
                    (row["id"] as Number).toLong()
                } else {
                    jdbc.queryForObject("INSERT INTO vocabularies(word,translation,phonetic,part_of_speech,definition,example_sentence,level) VALUES (?,?,?,?,?,?,?) RETURNING id", Long::class.java,
                        s(word,"word"),s(word,"translation"),s(word,"phonetic"),s(word,"partOfSpeech"),s(word,"definition"),s(word,"example"),s(word,"level"))!!
                }
                jdbc.update("INSERT INTO topic_vocabularies VALUES (?,?,?)", uuid(topic),id,root["vocabularies"].toList().indexOf(word))
                s(word,"word") to (id to word)
            }
            root["lessons"].forEach { lesson ->
                val revision = lesson["revision"]; val revisionId = uuid(revision)
                jdbc.update("INSERT INTO lessons VALUES (?,?,?,?,?)",uuid(lesson),uuid(unit),s(lesson,"title"),lesson["position"].asInt(),lesson["estimatedMinutes"].asInt())
                jdbc.update("INSERT INTO lesson_revisions(id,lesson_id,revision_no,introduction,sentence_patterns,status,published_at,created_at,content_status,pack_id) VALUES (?,?,?,?,CAST(? AS JSONB),'DRAFT',?,?,'DEMO',?)",
                    revisionId,uuid(lesson),revision["revisionNo"].asInt(),s(revision,"introduction"),mapper.writeValueAsString(revision["sentencePatterns"]),now,now,pack)
                lesson["vocabularyWords"].forEachIndexed { index, word ->
                    val (id, content) = vocabulary.getValue(word.asText())
                    jdbc.update("INSERT INTO lesson_vocabularies VALUES (?,?,?,CAST(? AS JSONB))",revisionId,id,index+1,mapper.writeValueAsString(content))
                }
                lesson["questions"].forEach { question ->
                    jdbc.update("INSERT INTO questions(id,lesson_revision_id,type,prompt,explanation,answer_key,position,weight) VALUES (?,?,?,?,?,CAST(? AS JSONB),?,?)",
                        uuid(question),revisionId,s(question,"type"),s(question,"prompt"),s(question,"explanation"),mapper.writeValueAsString(question["answerKey"]),question["position"].asInt(),question["weight"].asInt())
                    question["options"].forEach { option -> jdbc.update("INSERT INTO question_options VALUES (?,?,?,?)",uuid(question),s(option,"key"),s(option,"label"),option["position"].asInt()) }
                }
                jdbc.update("UPDATE lesson_revisions SET status='PUBLISHED' WHERE id=?",revisionId)
            }
            true
        }!!
    }
    private fun uuid(node: JsonNode, field: String = "id") = UUID.fromString(s(node,field))
    private fun s(node: JsonNode, field: String) = node[field].asText()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
