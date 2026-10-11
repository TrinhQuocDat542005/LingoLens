package com.quocdat.lingolens.lesson

import com.quocdat.lingolens.assessment.core.*
import java.util.UUID

object PackGrader {
    fun grade(questions: List<PackedQuestion>, answers: List<SubmittedAnswer>): LessonGrade = LessonGrader.grade(
        questions.map { GradingQuestion(UUID.fromString(it.id),it.weight,it.options.map { o -> o.key }.toSet(),it.answerKey.optionKey) },answers)
}
