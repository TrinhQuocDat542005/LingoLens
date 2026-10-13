package com.quocdat.lingolens.assessment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.UUID

class TypedGraderVectorTests {
    @Test fun `backend and shared use the same typed vectors and single question marks`() {
        val vectors = jacksonObjectMapper().readTree(requireNotNull(javaClass.getResourceAsStream("/typed-assessment-vectors.json")))
        vectors.forEach { vector ->
            val questions = vector["questions"].mapIndexed { index, q ->
                GradingQuestion(UUID(0, index.toLong() + 1), q["weight"].asInt(), q["options"].map { it.asText() }.toSet(),
                    q["correctKey"].asText(), q["type"].asText(), q["acceptedAnswers"]?.map { it.asText() }?.toSet().orEmpty())
            }
            val answers = vector["questions"].mapIndexed { index, q ->
                SubmittedAnswer(questions[index].id, q["raw"].asText())
            }
            val backend = LessonGrader.grade(questions, answers)
            val shared = com.quocdat.lingolens.assessment.core.LessonGrader.grade(questions, answers)
            val expected = vector["expected"]
            assertEquals(shared, backend, vector["name"].asText())
            assertEquals(expected["correctCount"].asInt(), backend.correctCount)
            assertEquals(expected["questionCount"].asInt(), backend.questionCount)
            assertEquals(expected["percent"].asInt(), backend.scorePercent)
            assertEquals(expected["passed"].asBoolean(), backend.passed)
            assertEquals(expected["marks"].map { it.asBoolean() }, backend.marks.map { it.correct })
            assertEquals(answers.map { it.optionKey }, backend.marks.map { it.optionKey })
            questions.zip(answers).forEachIndexed { index, (question, answer) ->
                assertEquals(backend.marks[index], com.quocdat.lingolens.assessment.core.LessonGrader.mark(question, answer))
            }
        }
    }

    @Test fun `fill matching is locale independent and limited to approved variants`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val id = UUID(0, 1)
            val question = GradingQuestion(id, 1, emptySet(), "", "FILL_GAP", setOf("ice cream"))
            val raw = " ICE  CREAM "
            val mark = com.quocdat.lingolens.assessment.core.LessonGrader.mark(question, SubmittedAnswer(id, raw))
            assertTrue(mark.correct)
            assertEquals(raw, mark.optionKey)
            assertFalse(com.quocdat.lingolens.assessment.core.LessonGrader.mark(question, SubmittedAnswer(id, "icecream")).correct)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `invalid typed configuration and choice answer fail validation`() {
        val id = UUID(0, 1)
        val choice = GradingQuestion(id, 1, setOf("A", "B"), "A")
        fun mark(question: GradingQuestion, raw: String = "A") =
            com.quocdat.lingolens.assessment.core.LessonGrader.mark(question, SubmittedAnswer(id, raw))
        assertThrows(IllegalArgumentException::class.java) { mark(choice, "Z") }
        assertThrows(IllegalArgumentException::class.java) { mark(choice.copy(type = "UNKNOWN")) }
        assertThrows(IllegalArgumentException::class.java) { mark(choice.copy(weight = 0)) }
        assertThrows(IllegalArgumentException::class.java) { mark(choice.copy(type = "FILL_GAP",
            options = emptySet(), correctKey = "", acceptedAnswers = emptySet()), "word") }
        assertThrows(IllegalArgumentException::class.java) { mark(choice.copy(type = "FILL_GAP",
            options = emptySet(), correctKey = "", acceptedAnswers = setOf("   ")), "word") }
        assertThrows(IllegalArgumentException::class.java) { mark(choice.copy(type = "FILL_GAP",
            options = emptySet(), correctKey = "", acceptedAnswers = setOf("word")), "   ") }
        assertThrows(IllegalArgumentException::class.java) { mark(choice.copy(acceptedAnswers = setOf("word"))) }
    }

    @Test fun `typed aggregate rejects missing duplicate and foreign answers without treating them as wrong`() {
        val questions = listOf(GradingQuestion(UUID(0, 1), 1, emptySet(), "", "FILL_GAP", setOf("word")),
            GradingQuestion(UUID(0, 2), 1, setOf("A", "B"), "A", "LISTEN_CHOICE"))
        val good = listOf(SubmittedAnswer(questions[0].id, "word"), SubmittedAnswer(questions[1].id, "A"))
        listOf(good.dropLast(1), listOf(good[0], good[0]),
            listOf(good[0], SubmittedAnswer(UUID(0, 3), "A"))).forEach { answers ->
            assertThrows(IllegalArgumentException::class.java) { LessonGrader.grade(questions, answers) }
        }
        assertEquals(LessonGrader.grade(questions, good), LessonGrader.grade(questions, good.reversed()))
    }
}

