package com.quocdat.lingolens.assessment

import java.util.UUID

data class GradingQuestion(val id: UUID, val weight: Int, val options: Set<String>, val correctKey: String)
data class SubmittedAnswer(val questionId: UUID, val optionKey: String)
data class AnswerMark(val questionId: UUID, val optionKey: String, val correct: Boolean, val awardedWeight: Int)
data class LessonGrade(val correctCount: Int, val questionCount: Int, val earnedWeight: Int, val totalWeight: Int,
    val scorePercent: Int, val passed: Boolean, val marks: List<AnswerMark>)

object LessonGrader {
    fun grade(questions: List<GradingQuestion>, answers: List<SubmittedAnswer>): LessonGrade {
        require(questions.isNotEmpty() && questions.size <= 1000)
        require(questions.map { it.id }.distinct().size == questions.size)
        require(questions.all { it.weight in 1..10 && it.correctKey in it.options })
        require(answers.size == questions.size && answers.map { it.questionId }.distinct().size == answers.size) { "Answer every question exactly once" }
        val submitted = answers.associateBy { it.questionId }
        require(submitted.keys == questions.map { it.id }.toSet()) { "Answers do not belong to this revision" }
        val marks = questions.map { question ->
            val answer = submitted.getValue(question.id)
            require(answer.optionKey in question.options) { "Unknown answer option" }
            val correct = answer.optionKey == question.correctKey
            AnswerMark(question.id,answer.optionKey,correct,if (correct) question.weight else 0)
        }
        val earned = marks.sumOf { it.awardedWeight }; val total = questions.sumOf { it.weight }
        return LessonGrade(marks.count { it.correct },questions.size,earned,total,(100L*earned/total).toInt(),
            100L*earned >= 70L*total,marks)
    }
}
