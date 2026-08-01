package com.quocdat.lingolens.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.common.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class CustomAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        val code = request.getAttribute("exception_code") as? String ?: "UNAUTHORIZED"
        val message = request.getAttribute("exception_message") as? String ?: "Authentication is required"
        objectMapper.writeValue(response.outputStream, ApiError(code = code, message = message))
    }
}
