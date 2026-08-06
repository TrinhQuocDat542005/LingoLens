package com.quocdat.lingolens.data.remote.dto

data class CreateRecognitionRequest(val detectedLabel: String, val confidence: Float, val engine: String)
data class RecognitionHistoryDto(
    val id: Long,
    val detectedLabel: String,
    val confidence: Float,
    val engine: String?,
    val reported: Boolean,
    val createdAt: String
)
data class RecognitionPageDto(
    val content: List<RecognitionHistoryDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
data class CreateRecognitionReportRequest(val expectedLabel: String, val note: String?)
data class RecognitionReportDto(
    val id: Long,
    val historyId: Long,
    val expectedLabel: String,
    val actualLabel: String,
    val resolved: Boolean,
    val createdAt: String
)
