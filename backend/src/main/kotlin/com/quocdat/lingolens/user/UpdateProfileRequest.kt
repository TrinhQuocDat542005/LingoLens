package com.quocdat.lingolens.user

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name must not exceed 100 characters")
    val name: String,

    @field:Pattern(regexp = "B1|B2", message = "Target level must be B1 or B2")
    val targetLevel: String,

    @field:Min(1, message = "Daily goal must be at least 1")
    @field:Max(50, message = "Daily goal must not exceed 50")
    val dailyGoal: Int
)
