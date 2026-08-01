package com.quocdat.lingolens.common

import java.time.Instant

data class ApiError(
    val success: Boolean = false,
    val code: String,
    val message: String,
    val errors: List<String> = emptyList(),
    val timestamp: Instant = Instant.now()
)