package com.quocdat.lingolens.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and token management")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Map<String, String>> {
        val user = authService.register(request)
        return ResponseEntity.ok(mapOf("message" to "User registered successfully with ID: ${user.id}"))
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT tokens")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate expired Access Token using Refresh Token")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<TokenResponse> {
        val response = authService.refresh(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out and invalidate Refresh Token")
    fun logout(principal: Principal?): ResponseEntity<Map<String, String>> {
        if (principal != null) {
            authService.logout(principal.name)
            return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
        }
        return ResponseEntity.badRequest().body(mapOf("error" to "No active session found"))
    }
}
