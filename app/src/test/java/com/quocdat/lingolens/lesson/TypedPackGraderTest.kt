package com.quocdat.lingolens.lesson

import com.google.gson.JsonParser
import com.quocdat.lingolens.assessment.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.UUID

class TypedPackGraderTest {
    @Test fun `typed vectors agree across Android shared aggregate and single marks`() {
        val vectors = requireNotNull(javaClass.getResourceAsStream("/typed-assessment-vectors.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        vectors.forEach { node ->
            val vector = node.asJsonObject
            val questions = vector["questions"].asJsonArray.mapIndexed { index, question ->
                val q = question.asJsonObject
                val options = q["options"].asJsonArray.mapIndexed { position, key ->
                    PackedOption(key.asString, "Option " + key.asString, position + 1)
                }
                PackedQuestion(UUID(0, index.toLong() + 1).toString(), q["type"].asString, "Prompt", "Explain",
                    index + 1, q["weight"].asInt, options,
                    PackedAnswerKey(q["correctKey"].asString, q["acceptedAnswers"]?.asJsonArray?.map { it.asString }),
                    if (q["type"].asString == "LISTEN_CHOICE") "notebook" else null,
                    if (q["type"].asString == "LISTEN_CHOICE") "Chọn từ có nghĩa sổ tay." else null)
            }
            val answers = vector["questions"].asJsonArray.mapIndexed { index, question ->
                SubmittedAnswer(UUID.fromString(questions[index].id), question.asJsonObject["raw"].asString)
            }
            val grading = questions.map { GradingQuestion(UUID.fromString(it.id), it.weight,
                it.options.map { option -> option.key }.toSet(), it.answerKey.optionKey, it.type,
                it.answerKey.acceptedAnswers.orEmpty().toSet()) }
            val android = PackGrader.grade(questions, answers)
            val shared = LessonGrader.grade(grading, answers)
            val expected = vector["expected"].asJsonObject
            assertEquals(vector["name"].asString, shared, android)
            assertEquals(expected["correctCount"].asInt, android.correctCount)
            assertEquals(expected["questionCount"].asInt, android.questionCount)
            assertEquals(expected["percent"].asInt, android.scorePercent)
            assertEquals(expected["passed"].asBoolean, android.passed)
            assertEquals(expected["marks"].asJsonArray.map { it.asBoolean }, android.marks.map { it.correct })
            assertEquals(answers.map { it.optionKey }, android.marks.map { it.optionKey })
            questions.zip(answers).forEachIndexed { index, (question, answer) ->
                assertEquals(android.marks[index], PackGrader.mark(question, answer))
                assertEquals(android.marks[index], LessonGrader.mark(grading[index], answer))
            }
        }
    }

    @Test fun `normalization ignores device locale and does not rewrite raw answer`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val q = PackedQuestion(UUID(0, 1).toString(), "FILL_GAP", "Prompt", "Explain", 1, 1, emptyList(),
                PackedAnswerKey("", listOf("ice cream")))
            val raw = " ICE  CREAM "
            val mark = PackGrader.mark(q, SubmittedAnswer(UUID.fromString(q.id), raw))
            assertTrue(mark.correct)
            assertEquals(raw, mark.optionKey)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `unknown choice keys and invalid type configuration are rejected`() {
        val id = UUID(0, 1)
        val q = PackedQuestion(id.toString(), "MEANING_CHOICE", "Prompt", "Explain", 1, 1,
            listOf(PackedOption("A", "One", 1), PackedOption("B", "Two", 2)), PackedAnswerKey("A"))
        assertThrows(IllegalArgumentException::class.java) { PackGrader.mark(q, SubmittedAnswer(id, "Z")) }
        assertThrows(IllegalArgumentException::class.java) { PackGrader.mark(q.copy(type = "UNKNOWN"), SubmittedAnswer(id, "A")) }
        assertThrows(IllegalArgumentException::class.java) { PackGrader.mark(q.copy(type = "FILL_GAP",
            options = emptyList(), answerKey = PackedAnswerKey("", emptyList())), SubmittedAnswer(id, "word")) }
        assertThrows(IllegalArgumentException::class.java) { PackGrader.mark(q.copy(type = "FILL_GAP",
            options = emptyList(), answerKey = PackedAnswerKey("", listOf("   "))), SubmittedAnswer(id, "word")) }
        assertThrows(IllegalArgumentException::class.java) { PackGrader.mark(q.copy(type = "FILL_GAP",
            options = emptyList(), answerKey = PackedAnswerKey("", listOf("word"))), SubmittedAnswer(id, "   ")) }
        assertThrows(IllegalArgumentException::class.java) { PackGrader.mark(q, SubmittedAnswer(UUID(0, 2), "A")) }
    }

    @Test fun `legacy choice JSON lacks typed metadata but remains gradable`() {
        val questions = requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonArray }.map { body ->
                com.google.gson.Gson().fromJson(body, LessonPack::class.java).questions
            }.flatten()
        questions.forEach { q ->
            assertNull(q.speechText)
            assertNull(q.fallbackPrompt)
            assertNull(q.answerKey.acceptedAnswers)
            assertTrue(PackGrader.mark(q, SubmittedAnswer(UUID.fromString(q.id), q.answerKey.optionKey)).correct)
        }
    }
}

