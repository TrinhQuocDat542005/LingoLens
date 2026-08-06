package com.quocdat.lingolens.recognition

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateRecognitionRequest(
    @field:NotBlank @field:Size(max = 100) val detectedLabel: String,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0") val confidence: Float,
    @field:NotBlank @field:Size(max = 50) val engine: String
)

data class RecognitionHistoryResponse(
    val id: Long,
    val detectedLabel: String,
    val confidence: Float,
    val engine: String?,
    val reported: Boolean,
    val createdAt: Instant
)

data class CreateRecognitionReportRequest(
    @field:NotBlank @field:Size(max = 100) val expectedLabel: String,
    @field:Size(max = 1000) val note: String? = null
)

data class UserRecognitionReportResponse(
    val id: Long,
    val historyId: Long,
    val expectedLabel: String,
    val actualLabel: String,
    val resolved: Boolean,
    val createdAt: Instant
)
