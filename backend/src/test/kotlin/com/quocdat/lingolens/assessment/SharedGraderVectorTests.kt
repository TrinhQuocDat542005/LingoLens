package com.quocdat.lingolens.assessment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class SharedGraderVectorTests {
    @Test fun `backend adapter matches shared grader and identical Android vectors`() {
        val vectors = jacksonObjectMapper().readTree(requireNotNull(javaClass.getResourceAsStream("/assessment-vectors.json")))
        vectors.forEach { v ->
            val questions = v["weights"].mapIndexed { i,w -> GradingQuestion(UUID(0,i.toLong()+1),w.asInt(),setOf("A","B","C","D"),"A") }
            val answers = questions.mapIndexed { i,q -> SubmittedAnswer(q.id,if(i<v["correct"].asInt()) "A" else "B") }
            val backend = LessonGrader.grade(questions,answers)
            assertEquals(com.quocdat.lingolens.assessment.core.LessonGrader.grade(questions,answers),backend)
            assertEquals(v["percent"].asInt(),backend.scorePercent); assertEquals(v["passed"].asBoolean(),backend.passed)
            assertEquals(v["correct"].asInt(),backend.correctCount)
        }
    }
}
