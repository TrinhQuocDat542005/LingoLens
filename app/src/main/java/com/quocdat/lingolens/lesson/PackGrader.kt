package com.quocdat.lingolens.lesson

import com.quocdat.lingolens.assessment.core.*
import java.util.UUID

object PackGrader {
    fun grade(questions: List<PackedQuestion>, answers: List<SubmittedAnswer>): LessonGrade =
        LessonGrader.grade(questions.map(::toGradingQuestion), answers)

    fun mark(question: PackedQuestion, answer: SubmittedAnswer): AnswerMark =
        LessonGrader.mark(toGradingQuestion(question), answer)

    private fun toGradingQuestion(question: PackedQuestion) = GradingQuestion(UUID.fromString(question.id), question.weight,
        question.options.map { it.key }.toSet(), question.answerKey.optionKey, question.type,
        question.answerKey.acceptedAnswers.orEmpty().toSet())
}

