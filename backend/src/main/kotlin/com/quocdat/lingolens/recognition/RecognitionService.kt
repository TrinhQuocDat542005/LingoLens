package com.quocdat.lingolens.recognition

import com.quocdat.lingolens.admin.PageResponse
import com.quocdat.lingolens.admin.RecognitionHistory
import com.quocdat.lingolens.admin.RecognitionHistoryRepository
import com.quocdat.lingolens.admin.RecognitionReport
import com.quocdat.lingolens.admin.RecognitionReportRepository
import com.quocdat.lingolens.common.UserNotFoundException
import com.quocdat.lingolens.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecognitionService(
    private val users: UserRepository,
    private val histories: RecognitionHistoryRepository,
    private val reports: RecognitionReportRepository
) {
    @Transactional
    fun create(request: CreateRecognitionRequest, email: String): RecognitionHistoryResponse {
        val user = requireUser(email)
        val history = histories.save(RecognitionHistory(
            user = user,
            detectedLabel = request.detectedLabel.trim().lowercase(),
            confidence = request.confidence,
            engine = request.engine.trim().uppercase()
        ))
        return history.toResponse(false)
    }

    @Transactional(readOnly = true)
    fun list(email: String, page: Int, size: Int): PageResponse<RecognitionHistoryResponse> {
        val user = requireUser(email)
        val result = histories.findAllByUser(user, PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 50), Sort.by("createdAt").descending()))
        return PageResponse(result.content.map { it.toResponse(reports.existsByHistory(it)) }, result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional
    fun report(historyId: Long, request: CreateRecognitionReportRequest, email: String): UserRecognitionReportResponse {
        val user = requireUser(email)
        val history = histories.findByIdAndUser(historyId, user)
            .orElseThrow { IllegalArgumentException("Không tìm thấy lịch sử nhận diện") }
        require(!reports.existsByHistory(history)) { "Lượt nhận diện này đã được báo cáo" }
        val expected = request.expectedLabel.trim().lowercase()
        require(expected != history.detectedLabel.lowercase()) { "Nhãn đúng phải khác kết quả nhận diện" }
        val saved = reports.save(RecognitionReport(
            user = user,
            history = history,
            expectedLabel = expected,
            actualLabel = history.detectedLabel,
            confidence = history.confidence,
            note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        ))
        return UserRecognitionReportResponse(saved.id!!, history.id!!, saved.expectedLabel, saved.actualLabel, saved.resolved, saved.createdAt)
    }

    private fun requireUser(email: String) = users.findByEmail(email).orElseThrow { UserNotFoundException("Không tìm thấy người dùng") }
    private fun RecognitionHistory.toResponse(reported: Boolean) = RecognitionHistoryResponse(id!!, detectedLabel, confidence, engine, reported, createdAt)
}
