package com.quocdat.lingolens.auth

import com.quocdat.lingolens.common.ApiResponse
import com.quocdat.lingolens.user.UserProfileResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthController(private val authService: AuthService) {
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<ApiResponse<UserProfileResponse>> {
        val user = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Registration successful", UserProfileResponse.from(user)))
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in and receive an access/refresh token pair")
    fun login(@Valid @RequestBody body: LoginRequest, request: HttpServletRequest) =
        ApiResponse.success("Login successful", authService.login(body, request.getHeader("User-Agent")))

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new token pair")
    fun refresh(@Valid @RequestBody body: RefreshTokenRequest, request: HttpServletRequest) =
        ApiResponse.success("Token refreshed", authService.refresh(body, request.getHeader("User-Agent")))

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current device refresh token", security = [SecurityRequirement(name = "BearerAuth")])
    fun logout(@Valid @RequestBody body: LogoutRequest, principal: Principal): ApiResponse<Unit> {
        authService.logout(body.refreshToken, principal.name)
        return ApiResponse.success("Logged out successfully")
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Revoke all refresh tokens", security = [SecurityRequirement(name = "BearerAuth")])
    fun logoutAll(principal: Principal): ApiResponse<Unit> {
        authService.logoutAll(principal.name)
        return ApiResponse.success("All sessions have been logged out")
    }
}
