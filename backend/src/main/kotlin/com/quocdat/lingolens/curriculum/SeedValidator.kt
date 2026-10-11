package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

object SeedValidator {
    fun validate(root: JsonNode) {
        fields(root, "schemaVersion", "packId", "contentStatus", "pronunciationLocale", "course", "topic", "unit", "vocabularies", "lessons")
        number(root, "schemaVersion", 1, 1); id(root, "packId")
        require(text(root, "contentStatus", 20) in setOf("DEMO", "REVIEWED"))
        require(text(root, "pronunciationLocale", 10) == "en-US")
        val course = root["course"]; fields(course, "id", "code", "title", "description", "level")
        id(course); text(course, "code", 60); text(course, "title", 150); text(course, "description", 2000)
        require(text(course, "level", 10) == "A1")
        val topic = root["topic"]; fields(topic, "id", "slug", "title", "description", "iconKey", "displayOrder")
        id(topic); text(topic, "slug", 80); text(topic, "title", 120); text(topic, "description", 2000); text(topic, "iconKey", 50); number(topic, "displayOrder", 0, 1000)
        val unit = root["unit"]; fields(unit, "id", "courseId", "topicId", "title", "position")
        id(unit); require(id(unit, "courseId") == id(course)); require(id(unit, "topicId") == id(topic))
        text(unit, "title", 150); number(unit, "position", 1, 1000)
        val words = array(root, "vocabularies", 15, 18)
        words.forEach {
            fields(it, "word", "translation", "phonetic", "partOfSpeech", "definition", "example", "level")
            val word = text(it, "word", 100); require(word == word.trim().lowercase())
            text(it, "translation", 255); text(it, "phonetic", 100); text(it, "partOfSpeech", 50)
            text(it, "definition", 2000); text(it, "example", 2000); require(text(it, "level", 10) == "A1")
        }
        unique(words.map { it["word"].asText() })
        val allIds = mutableListOf(id(root, "packId"), id(course), id(topic), id(unit))
        val lessons = array(root, "lessons", 3, 3)
        unique(lessons.map { number(it, "position", 1, 3) }); require(lessons.map { it["position"].asInt() }.sorted() == listOf(1,2,3))
        lessons.forEach { lesson ->
            fields(lesson, "id", "unitId", "title", "position", "estimatedMinutes", "revision", "vocabularyWords", "questions")
            allIds += id(lesson); require(id(lesson, "unitId") == id(unit)); text(lesson, "title", 150)
            number(lesson, "estimatedMinutes", 1, 30)
            val revision = lesson["revision"]; fields(revision, "id", "revisionNo", "introduction", "sentencePatterns")
            allIds += id(revision); number(revision, "revisionNo", 1, 1000); text(revision, "introduction", 4000)
            array(revision, "sentencePatterns", 1, 2).forEach {
                fields(it, "pattern", "example", "translation"); text(it,"pattern",500); text(it,"example",500); text(it,"translation",500)
            }
            val vocabulary = array(lesson, "vocabularyWords", 5, 8).map { require(it.isTextual); it.asText() }
            unique(vocabulary); require(vocabulary.all { word -> words.any { it["word"].asText() == word } })
            val questions = array(lesson, "questions", 8, 10)
            unique(questions.map { number(it, "position", 1, questions.size) })
            questions.forEach { question ->
                fields(question, "id", "type", "prompt", "explanation", "position", "weight", "options", "answerKey")
                allIds += id(question); require(text(question,"type",20) == "MEANING_CHOICE")
                text(question,"prompt",1000); text(question,"explanation",2000); number(question,"weight",1,10)
                if (root["contentStatus"].asText() == "DEMO") require(question["weight"].asInt() == 1)
                val options = array(question,"options",4,4)
                options.forEach { fields(it,"key","label","position"); text(it,"key",8); text(it,"label",500); number(it,"position",1,4) }
                unique(options.map { it["key"].asText() }); unique(options.map { it["position"].asInt() })
                fields(question["answerKey"],"optionKey")
                require(text(question["answerKey"],"optionKey",8) in options.map { it["key"].asText() })
            }
        }
        unique(allIds)
    }
    private fun fields(node: JsonNode, vararg required: String) {
        require(node.isObject && node.fieldNames().asSequence().toSet() == required.toSet()) { "Unexpected or missing seed fields: ${required.joinToString()}" }
    }
    private fun text(node: JsonNode, field: String, max: Int): String {
        val value = node[field]; require(value.isTextual && value.asText().isNotBlank() && value.asText().length <= max) { "Invalid seed $field" }
        return value.asText()
    }
    private fun id(node: JsonNode, field: String = "id") = UUID.fromString(text(node,field,36))
    private fun number(node: JsonNode, field: String, min: Int, max: Int): Int {
        val value = node[field]; require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() in min..max) { "Invalid seed $field" }; return value.asInt()
    }
    private fun array(node: JsonNode, field: String, min: Int, max: Int): List<JsonNode> {
        val value = node[field]; require(value.isArray && value.size() in min..max) { "Invalid seed $field" }; return value.toList()
    }
    private fun <T> unique(values: List<T>) { require(values.distinct().size == values.size) { "Duplicate seed IDs, words or positions" } }
}
