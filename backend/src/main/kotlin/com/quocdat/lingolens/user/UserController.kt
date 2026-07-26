package com.quocdat.lingolens.user

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "Endpoints for managing user profiles")
class UserController {

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile", security = [SecurityRequirement(name = "BearerAuth")])
    fun getProfile(@AuthenticationPrincipal user: User): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "id" to (user.id ?: 0L),
            "email" to user.getEmail(),
            "name" to user.getName(),
            "targetLevel" to user.targetLevel,
            "streakDays" to user.streakDays,
            "roles" to user.roles.map { it.name }
        ))
    }
}
