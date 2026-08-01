package com.quocdat.lingolens.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.common.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class CustomAccessDeniedHandler(private val objectMapper: ObjectMapper) : AccessDeniedHandler {
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, exception: AccessDeniedException) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(
            response.outputStream,
            ApiError(code = "ACCESS_DENIED", message = "You do not have permission to access this resource")
        )
    }
}
