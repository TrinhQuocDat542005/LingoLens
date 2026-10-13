package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.quocdat.lingolens.assessment.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PackWireCodecTests {
    private val mapper = jacksonObjectMapper()
    private val codec = PackWireCodec(mapper)
    private fun legacyPack(): LessonPack {
        val fixture = mapper.readTree(requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json")))
        return mapper.treeToValue(fixture[0], LessonPack::class.java)
    }

    @Test fun `production codec omits absent optional fields and roundtrips complete legacy JSON`() {
        val fixture = mapper.readTree(requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json")))
        fixture.forEach { body ->
            val pack = mapper.treeToValue(body, LessonPack::class.java)
            val bytes = codec.encode(pack)
            assertEquals(body, mapper.readTree(bytes))
            assertEquals(pack, mapper.readValue(bytes, LessonPack::class.java))
            val questions = mapper.readTree(bytes)["questions"]
            questions.forEach { question ->
                assertFalse(question.has("speechText"))
                assertFalse(question.has("fallbackPrompt"))
                assertFalse(question["answerKey"].has("acceptedAnswers"))
            }
        }
    }

    @Test fun `typed pack optional metadata is included and codec leaves global mapper unchanged`() {
        val canonical = linkedMapOf("answer" to " ICE  CREAM ", "legacyNull" to null)
        val before = mapper.writeValueAsBytes(canonical)
        assertTrue(mapper.readTree(before).has("legacyNull"))
        val old = legacyPack()
        val listen = old.questions[0].copy(type = "LISTEN_CHOICE", speechText = "notebook",
            fallbackPrompt = "Chọn từ có nghĩa sổ tay.")
        val fill = old.questions[1].copy(type = "FILL_GAP", options = emptyList(),
            answerKey = PackedAnswerKey("", listOf("ice cream", "gelato")))
        val pack = old.copy(questions = listOf(listen, fill) + old.questions.drop(2))
        val bytes = codec.encode(pack)
        val body = mapper.readTree(bytes)
        assertEquals("notebook", body["questions"][0]["speechText"].asText())
        assertEquals("Chọn từ có nghĩa sổ tay.", body["questions"][0]["fallbackPrompt"].asText())
        assertEquals(listOf("ice cream", "gelato"), body["questions"][1]["answerKey"]["acceptedAnswers"].map { it.asText() })
        assertEquals("", body["questions"][1]["answerKey"]["optionKey"].asText())
        assertEquals(pack, mapper.readValue(bytes, LessonPack::class.java))
        assertArrayEquals(before, mapper.writeValueAsBytes(canonical))
    }
}

