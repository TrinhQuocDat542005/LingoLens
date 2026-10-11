package com.quocdat.lingolens.assessment

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class LessonGraderTests {
    private fun questions(weights: List<Int>) = weights.mapIndexed { index, weight -> GradingQuestion(UUID(0,index.toLong()+1),weight,setOf("A","B","C","D"),"A") }
    private fun answers(questions: List<GradingQuestion>, correct: Int) = questions.mapIndexed { index,q -> SubmittedAnswer(q.id,if (index<correct) "A" else "B") }
    @Test fun `exact 70 percent passes without floating point`() {
        val questions = questions(List(10) {1}); val grade = LessonGrader.grade(questions,answers(questions,7))
        assertTrue(grade.passed); assertEquals(7,grade.correctCount); assertEquals(10,grade.questionCount); assertEquals(70,grade.scorePercent)
        assertFalse(LessonGrader.grade(questions,answers(questions,6)).passed)
    }
    @Test fun `eight equal weight questions require six correct`() {
        val questions = questions(List(8) {1})
        assertTrue(LessonGrader.grade(questions,answers(questions,6)).passed)
        assertFalse(LessonGrader.grade(questions,answers(questions,5)).passed)
    }
    @Test fun `weighted result below 70 never passes after rounding`() {
        val questions = questions(listOf(10,6,7)); val grade = LessonGrader.grade(questions,answers(questions,2))
        assertEquals(16,grade.earnedWeight); assertEquals(23,grade.totalWeight)
        assertEquals(2,grade.correctCount); assertEquals(3,grade.questionCount); assertEquals(69,grade.scorePercent); assertFalse(grade.passed)
    }
    @Test fun `above 70 passes and percentage is floor`() {
        val questions = questions(listOf(5,5,3)); val grade = LessonGrader.grade(questions,answers(questions,2))
        assertEquals(76,grade.scorePercent); assertTrue(grade.passed)
    }
    @Test fun `missing duplicate foreign question and unknown option are rejected`() {
        val questions = questions(List(8) {1}); val answers = answers(questions,8)
        listOf(answers.dropLast(1), answers.dropLast(1)+answers.first(), answers.dropLast(1)+SubmittedAnswer(UUID.randomUUID(),"A"),
            answers.dropLast(1)+SubmittedAnswer(questions.last().id,"Z")).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { LessonGrader.grade(questions,invalid) }
        }
    }
    @Test fun `answer order does not affect grading and marks contain server weights`() {
        val questions = questions(List(8) {1}); val answers = answers(questions,6)
        val grade = LessonGrader.grade(questions,answers)
        assertEquals(grade,LessonGrader.grade(questions,answers.reversed()))
        assertEquals(listOf(1,1,1,1,1,1,0,0),grade.marks.map { it.awardedWeight })
    }
}
