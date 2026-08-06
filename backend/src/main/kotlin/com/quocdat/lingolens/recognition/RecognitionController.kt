package com.quocdat.lingolens.recognition

import com.quocdat.lingolens.admin.PageResponse
import com.quocdat.lingolens.common.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/recognitions")
class RecognitionController(private val service: RecognitionService) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateRecognitionRequest, auth: Authentication) =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Recognition saved", service.create(request, auth.name)))

    @GetMapping
    fun list(auth: Authentication, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): ApiResponse<PageResponse<RecognitionHistoryResponse>> =
        ApiResponse.success("Recognition history loaded", service.list(auth.name, page, size))

    @PostMapping("/{historyId}/reports")
    fun report(@PathVariable historyId: Long, @Valid @RequestBody request: CreateRecognitionReportRequest, auth: Authentication) =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Recognition report submitted", service.report(historyId, request, auth.name)))
}
