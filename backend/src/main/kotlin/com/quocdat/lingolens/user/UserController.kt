package com.quocdat.lingolens.user

import com.quocdat.lingolens.common.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User")
@SecurityRequirement(name = "BearerAuth")
class UserController(private val userRepository: UserRepository) {
    @GetMapping("/me")
    @Operation(summary = "Get the authenticated profile")
    fun getProfile(@AuthenticationPrincipal user: User) =
        ApiResponse.success("Profile loaded", UserProfileResponse.from(user))

    @PutMapping("/me")
    @Transactional
    @Operation(summary = "Update the authenticated profile")
    fun updateProfile(@AuthenticationPrincipal user: User, @Valid @RequestBody body: UpdateProfileRequest): ApiResponse<UserProfileResponse> {
        user.updateProfile(body.name.trim(), body.targetLevel, body.dailyGoal)
        return ApiResponse.success("Profile updated", UserProfileResponse.from(userRepository.save(user)))
    }
}
