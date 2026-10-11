package com.quocdat.lingolens.assessment

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import java.util.UUID

data class SubmitLessonRequest(val id: UUID, val lessonRevisionId: UUID,
    @field:Pattern(regexp="LESSON|PRACTICE") val mode: String,
    @field:Min(1) val clientCompletedAt: Long, val submittedOffline: Boolean = false,
    @field:Valid @field:Size(min=8,max=10) val answers: List<SubmittedAnswer>)
data class AttemptResult(val id: UUID, val lessonId: UUID, val mode: String, val correctCount: Int,
    val questionCount: Int, val scorePercent: Int, val passed: Boolean, val xpAwarded: Int)
data class CourseCard(val id: UUID, val title: String, val contentStatus: String, val firstLessonId: UUID, val firstRevisionId: UUID, val completed: Boolean)
data class CurriculumCatalog(val courses: List<CourseCard>, val xpTotal: Int)
data class OptionView(val key: String, val label: String, val position: Int)
data class QuestionView(val id: UUID, val prompt: String, val position: Int, val options: List<OptionView>)
data class LessonView(val id: UUID, val revisionId: UUID, val title: String, val introduction: String,
    val contentStatus: String, val sentencePatterns: JsonNode, val vocabularies: List<JsonNode>, val questions: List<QuestionView>)
class LessonRequestException(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)
