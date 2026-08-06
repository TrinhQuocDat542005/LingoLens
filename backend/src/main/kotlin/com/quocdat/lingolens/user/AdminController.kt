package com.quocdat.lingolens.user

import com.quocdat.lingolens.admin.*
import com.quocdat.lingolens.common.ApiResponse
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("api/v1/admin")
@Tag(name = "Admin", description = "Endpoints dành riêng cho quản trị viên (ADMIN)")
class AdminController(private val service: AdminService) {

    @GetMapping("/hello")
    @Operation(summary = "Endpoint test phân quyền admin", security = [SecurityRequirement(name = "BearerAuth")])
    fun adminHello(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Hello Admin! Bạn đang truy cập với quyền ADMIN."))
    }

    @GetMapping("/dashboard") fun dashboard() = ApiResponse.success("Dashboard loaded", service.dashboard())
    @GetMapping("/users") fun users(@RequestParam(defaultValue = "") search: String, @RequestParam(required = false) enabled: Boolean?, @RequestParam(required = false) role: String?, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = ApiResponse.success("Users loaded", service.listUsers(search, enabled, role, page, size))
    @PatchMapping("/users/{id}/status") fun status(@PathVariable id: Long, @RequestBody request: UpdateUserStatusRequest, auth: Authentication) = ApiResponse.success("User status updated", service.updateStatus(id, request, auth.name))
    @PatchMapping("/users/{id}/roles") fun roles(@PathVariable id: Long, @RequestBody request: UpdateUserRolesRequest, auth: Authentication) = ApiResponse.success("User roles updated", service.updateRoles(id, request, auth.name))
    @GetMapping("/vocabularies") fun vocabularies(@RequestParam(defaultValue = "") search: String, @RequestParam(required = false) published: Boolean?, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = ApiResponse.success("Vocabularies loaded", service.listVocabularies(search, published, page, size))
    @PostMapping("/vocabularies") fun createVocabulary(@Valid @RequestBody request: VocabularyRequest, auth: Authentication) = ApiResponse.success("Vocabulary created", service.createVocabulary(request, auth.name))
    @PutMapping("/vocabularies/{id}") fun updateVocabulary(@PathVariable id: Long, @Valid @RequestBody request: VocabularyRequest, auth: Authentication) = ApiResponse.success("Vocabulary updated", service.updateVocabulary(id, request, auth.name))
    @DeleteMapping("/vocabularies/{id}") fun deleteVocabulary(@PathVariable id: Long, auth: Authentication): ApiResponse<Unit> { service.deleteVocabulary(id, auth.name); return ApiResponse.success("Vocabulary deleted") }
    @GetMapping("/reports") fun reports(@RequestParam(required = false) resolved: Boolean?, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = ApiResponse.success("Reports loaded", service.listReports(resolved, page, size))
    @PatchMapping("/reports/{id}/resolve") fun resolve(@PathVariable id: Long, @RequestBody request: ResolveReportRequest, auth: Authentication) = ApiResponse.success("Report updated", service.resolveReport(id, request, auth.name))
    @GetMapping("/audit-logs") fun audits() = ApiResponse.success("Audit logs loaded", service.auditLogs())
}
