package com.quocdat.lingolens.assessment.core

import java.util.Locale
import java.util.UUID

data class GradingQuestion(val id: UUID, val weight: Int, val options: Set<String>, val correctKey: String,
    val type: String = "MEANING_CHOICE", val acceptedAnswers: Set<String> = emptySet())
data class SubmittedAnswer(val questionId: UUID, val optionKey: String)
data class AnswerMark(val questionId: UUID, val optionKey: String, val correct: Boolean, val awardedWeight: Int)
data class LessonGrade(val correctCount: Int, val questionCount: Int, val earnedWeight: Int, val totalWeight: Int,
    val scorePercent: Int, val passed: Boolean, val marks: List<AnswerMark>)

object LessonGrader {
    private val whitespace = Regex("""[\s\p{Z}]+""")

    /** Limited matching only. The submitted raw value is retained in every answer mark and payload. */
    fun normalizeFill(value: String): String = value.replace(whitespace, " ").trim().lowercase(Locale.ROOT)

    fun mark(question: GradingQuestion, answer: SubmittedAnswer): AnswerMark {
        require(question.weight in 1..10)
        require(answer.questionId == question.id) { "Answer does not belong to this question" }
        val correct = when (question.type) {
            "MEANING_CHOICE", "LISTEN_CHOICE" -> {
                require(question.options.isNotEmpty() && question.correctKey in question.options &&
                    question.acceptedAnswers.isEmpty()) { "Invalid choice answer key" }
                require(answer.optionKey in question.options) { "Unknown answer option" }
                answer.optionKey == question.correctKey
            }
            "FILL_GAP" -> {
                require(question.options.isEmpty() && question.correctKey.isEmpty() &&
                    question.acceptedAnswers.isNotEmpty() &&
                    question.acceptedAnswers.all { normalizeFill(it).isNotBlank() }) { "Invalid fill answer key" }
                val normalized = normalizeFill(answer.optionKey)
                require(normalized.isNotBlank()) { "Answer every fill question" }
                question.acceptedAnswers.any { normalizeFill(it) == normalized }
            }
            else -> throw IllegalArgumentException("Unknown question type")
        }
        return AnswerMark(question.id, answer.optionKey, correct, if (correct) question.weight else 0)
    }

    fun grade(questions: List<GradingQuestion>, answers: List<SubmittedAnswer>): LessonGrade {
        require(questions.isNotEmpty() && questions.size <= 1000)
        require(questions.map { it.id }.distinct().size == questions.size)
        require(answers.size == questions.size && answers.map { it.questionId }.distinct().size == answers.size) {
            "Answer every question exactly once"
        }
        val submitted = answers.associateBy { it.questionId }
        require(submitted.keys == questions.map { it.id }.toSet()) { "Answers do not belong to this revision" }
        val marks = questions.map { question -> mark(question, submitted.getValue(question.id)) }
        val earned = marks.sumOf { it.awardedWeight }
        val total = questions.sumOf { it.weight }
        return LessonGrade(marks.count { it.correct }, questions.size, earned, total, (100L * earned / total).toInt(),
            100L * earned >= 70L * total, marks)
    }
}

