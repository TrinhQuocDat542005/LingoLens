package com.quocdat.lingolens.lesson

import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import retrofit2.Response
import retrofit2.http.*

data class CourseDto(val id: String,val title: String,val contentStatus: String,val firstLessonId: String,val firstRevisionId: String,val completed: Boolean)
data class CatalogDto(val courses: List<CourseDto>,val xpTotal: Int)
data class PatternDto(val pattern: String,val example: String,val translation: String)
data class WordDto(val word: String,val translation: String,val phonetic: String,val partOfSpeech: String,val definition: String,val example: String,val level: String)
data class OptionDto(val key: String,val label: String,val position: Int)
data class QuestionDto(val id: String,val prompt: String,val position: Int,val options: List<OptionDto>)
data class LessonDto(val id: String,val revisionId: String,val title: String,val introduction: String,val contentStatus: String,
    val sentencePatterns: List<PatternDto>,val vocabularies: List<WordDto>,val questions: List<QuestionDto>)
data class AnswerDto(val questionId: String,val optionKey: String)
data class AttemptRequestDto(val id: String,val lessonRevisionId: String,val mode: String,val clientCompletedAt: Long,val submittedOffline: Boolean,val answers: List<AnswerDto>)
data class AttemptResultDto(val id: String,val lessonId: String,val mode: String,val correctCount: Int,val questionCount: Int,val scorePercent: Int,val passed: Boolean,val xpAwarded: Int)

interface LessonApi {
    @GET("api/v1/curriculum") suspend fun catalog(): Response<ApiEnvelope<CatalogDto>>
    @POST("api/v1/curriculum/courses/{id}/enroll") suspend fun enroll(@Path("id") id: String): Response<ApiEnvelope<Unit>>
    @GET("api/v1/curriculum/lessons/{id}") suspend fun lesson(@Path("id") id: String): Response<ApiEnvelope<LessonDto>>
    @POST("api/v1/lesson-attempts") suspend fun submit(@Body request: AttemptRequestDto): Response<ApiEnvelope<AttemptResultDto>>
}
interface LessonGateway {
    suspend fun catalog(): CatalogDto
    suspend fun enroll(courseId: String)
    suspend fun lesson(id: String): LessonDto
    suspend fun submit(request: AttemptRequestDto): AttemptResultDto
}
class LessonRepository(private val api: LessonApi) : LessonGateway {
    override suspend fun catalog()=data(api.catalog())
    override suspend fun enroll(courseId: String) {
        val response=api.enroll(courseId)
        checkResponse(response)
    }
    override suspend fun lesson(id: String)=data(api.lesson(id))
    override suspend fun submit(request: AttemptRequestDto)=data(api.submit(request))
    private fun checkResponse(response: Response<*>) {
        if (!response.isSuccessful) throw IllegalStateException(when(response.code()) {
            401 -> "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
            409 -> "Yêu cầu xung đột với dữ liệu đã ghi nhận."
            404 -> "Không tìm thấy nội dung hoặc kết quả bài học."
            else -> "Chưa thể hoàn thành yêu cầu. Vui lòng thử lại."
        })
    }
    private fun <T> data(response: Response<ApiEnvelope<T>>): T {
        checkResponse(response)
        return requireNotNull(response.body()?.takeIf { it.success }?.data) { "Phản hồi bài học không hợp lệ" }
    }
}
