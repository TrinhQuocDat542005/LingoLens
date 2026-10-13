package com.quocdat.lingolens.assessment

typealias GradingQuestion = com.quocdat.lingolens.assessment.core.GradingQuestion
typealias SubmittedAnswer = com.quocdat.lingolens.assessment.core.SubmittedAnswer
typealias AnswerMark = com.quocdat.lingolens.assessment.core.AnswerMark
typealias LessonGrade = com.quocdat.lingolens.assessment.core.LessonGrade
object LessonGrader {
    fun grade(questions: List<GradingQuestion>, answers: List<SubmittedAnswer>): LessonGrade =
        com.quocdat.lingolens.assessment.core.LessonGrader.grade(questions, answers)
}
