package com.quocdat.lingolens.lesson

import com.google.gson.JsonParser
import com.quocdat.lingolens.assessment.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PackGraderTest {
    @Test fun `Android mapper agrees with shared grader and approved vectors`() {
        val vectors = requireNotNull(javaClass.getResourceAsStream("/assessment-vectors.json")).bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        vectors.forEach { node ->
            val v = node.asJsonObject
            val weights = v["weights"].asJsonArray.map { it.asInt }
            val questions = weights.mapIndexed { i, w -> PackedQuestion(UUID(0,i.toLong()+1).toString(), "MEANING_CHOICE", "Prompt", "Explain", i+1, w,
                listOf(PackedOption("A","Yes",1),PackedOption("B","No",2),PackedOption("C","Other",3),PackedOption("D","Else",4)), PackedAnswerKey("A")) }
            val answers = questions.mapIndexed { i,q -> SubmittedAnswer(UUID.fromString(q.id),if(i<v["correct"].asInt) "A" else "B") }
            val android = PackGrader.grade(questions,answers)
            val server = LessonGrader.grade(questions.map { GradingQuestion(UUID.fromString(it.id),it.weight,it.options.map { o -> o.key }.toSet(),it.answerKey.optionKey) },answers)
            assertEquals(server,android)
            assertEquals(v["percent"].asInt,android.scorePercent); assertEquals(v["passed"].asBoolean,android.passed)
            assertEquals(v["correct"].asInt,android.correctCount)
        }
    }
}
