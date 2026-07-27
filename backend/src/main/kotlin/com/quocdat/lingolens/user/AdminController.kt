package com.quocdat.lingolens.user

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("api/v1/admin")
@Tag(name = "Admin", description = "Endpoints dành riêng cho quản trị viên (ADMIN)")
class AdminController{

    @GetMapping("/hello")
    @Operation(summary = "Endpoint test phân quyền admin", security = [SecurityRequirement(name = "BearerAuth")])
    fun adminHello(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Hello Admin! Bạn đang truy cập với quyền ADMIN."))
    }
}