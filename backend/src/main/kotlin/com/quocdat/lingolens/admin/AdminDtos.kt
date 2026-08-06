package com.quocdat.lingolens.admin

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class AdminDashboardResponse(
    val totalUsers: Long, val activeUsers: Long, val disabledUsers: Long, val adminUsers: Long,
    val newUsersLast7Days: Long, val totalVocabularies: Long, val publishedVocabularies: Long,
    val totalScans: Long, val scansLast7Days: Long, val pendingReports: Long,
    val userGrowth: List<DailyMetric>, val recentActivity: List<AuditLogResponse>
)
data class DailyMetric(val date: String, val value: Long)
data class PageResponse<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)
data class AdminUserResponse(
    val id: Long, val email: String, val name: String, val targetLevel: String, val dailyGoal: Int,
    val streakDays: Int, val enabled: Boolean, val roles: List<String>, val createdAt: Instant, val updatedAt: Instant
)
data class UpdateUserStatusRequest(val enabled: Boolean)
data class UpdateUserRolesRequest(val roles: Set<String>)

data class VocabularyRequest(
    @field:NotBlank @field:Size(max = 100) val word: String,
    @field:NotBlank @field:Size(max = 255) val translation: String,
    @field:Size(max = 100) val phonetic: String? = null,
    @field:NotBlank @field:Size(max = 50) val partOfSpeech: String,
    @field:NotBlank val definition: String,
    val exampleSentence: String? = null, val exampleSentenceB2: String? = null, val synonyms: String? = null,
    @field:Pattern(regexp = "A1|A2|B1|B2|C1|C2") val level: String,
    val published: Boolean = true
)
data class VocabularyResponse(
    val id: Long, val word: String, val translation: String, val phonetic: String?, val partOfSpeech: String,
    val definition: String, val exampleSentence: String?, val exampleSentenceB2: String?, val synonyms: String?,
    val level: String, val published: Boolean, val createdAt: Instant, val updatedAt: Instant
)
data class RecognitionReportResponse(
    val id: Long, val userId: Long, val userEmail: String, val expectedLabel: String, val actualLabel: String,
    val confidence: Float?, val note: String?, val resolved: Boolean, val resolvedAt: Instant?, val resolvedBy: String?,
    val createdAt: Instant
)
data class ResolveReportRequest(val resolved: Boolean = true)
data class AuditLogResponse(
    val id: Long, val adminEmail: String, val action: String, val targetType: String,
    val targetId: String?, val details: String?, val createdAt: Instant
)
