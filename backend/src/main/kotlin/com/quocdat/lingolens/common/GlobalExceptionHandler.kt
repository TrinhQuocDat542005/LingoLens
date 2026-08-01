package com.quocdat.lingolens.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", errors)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ApiError> {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message ?: "Bad request")
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailExists(ex: EmailAlreadyExistsException) =
        error(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", ex.message ?: "Email already exists")

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException) =
        error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.message ?: "User not found")

    @ExceptionHandler(TokenExpiredException::class)
    fun handleExpiredToken(ex: TokenExpiredException) =
        error(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", ex.message ?: "Token expired")

    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidToken(ex: InvalidTokenException) =
        error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", ex.message ?: "Invalid token")

    @ExceptionHandler(AccountDisabledException::class)
    fun handleDisabled(ex: AccountDisabledException) =
        error(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", ex.message ?: "Account disabled")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException) =
        error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to access this resource")

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(ex: IllegalStateException): ResponseEntity<ApiError> {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error")
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentialsException(ex: BadCredentialsException): ResponseEntity<ApiError> {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect")
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ApiError> {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred")
    }

    private fun error(status: HttpStatus, code: String, message: String, errors: List<String> = emptyList()) =
        ResponseEntity.status(status).body(ApiError(code = code, message = message, errors = errors))
}
