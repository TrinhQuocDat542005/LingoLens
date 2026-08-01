package com.quocdat.lingolens.common

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean = true,
    val message: String,
    val data: T? = null,
    val timestamp: Instant = Instant.now()
) {
    companion object {
        fun <T> success(message: String, data: T? = null): ApiResponse<T> {
            return ApiResponse(success = true, message = message, data = data)
        }
    }
}
