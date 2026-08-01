package com.quocdat.lingolens.auth

import com.quocdat.lingolens.common.AccountDisabledException
import com.quocdat.lingolens.common.EmailAlreadyExistsException
import com.quocdat.lingolens.common.InvalidTokenException
import com.quocdat.lingolens.common.TokenExpiredException
import com.quocdat.lingolens.common.UserNotFoundException
import com.quocdat.lingolens.security.JwtService
import com.quocdat.lingolens.security.RefreshToken
import com.quocdat.lingolens.security.RefreshTokenRepository
import com.quocdat.lingolens.security.RoleRepository
import com.quocdat.lingolens.user.User
import com.quocdat.lingolens.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager,
    @Value("\${security.jwt.expiration-time}") private val accessExpirationTime: Long,
    @Value("\${security.jwt.refresh-expiration-time}") private val refreshExpirationTime: Long
) {
    @Transactional
    fun register(request: RegisterRequest): User {
        val normalizedEmail = request.email.trim().lowercase()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw EmailAlreadyExistsException("Email is already registered")
        }
        val userRole = roleRepository.findByName("ROLE_USER")
            .orElseThrow { IllegalStateException("Default role ROLE_USER not found") }
        return userRepository.save(
            User(
                email = normalizedEmail,
                password = passwordEncoder.encode(request.password),
                name = request.name.trim(),
                roles = mutableSetOf(userRole)
            )
        )
    }

    @Transactional
    fun login(request: LoginRequest, deviceInfo: String?): TokenResponse {
        val email = request.email.trim().lowercase()
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken(email, request.password))
        val user = userRepository.findByEmail(email).orElseThrow { UserNotFoundException("User not found") }
        if (!user.enabled) throw AccountDisabledException("This account has been disabled")
        return issueTokenPair(user, deviceInfo)
    }

    @Transactional
    fun refresh(request: RefreshTokenRequest, deviceInfo: String?): TokenResponse {
        val token = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken))
            .orElseThrow { InvalidTokenException("Refresh token is invalid") }
        if (token.revokedAt != null) throw InvalidTokenException("Refresh token has been revoked")
        if (token.expiryDate.isBefore(Instant.now())) {
            token.revokedAt = Instant.now()
            throw TokenExpiredException("Refresh token has expired. Please sign in again")
        }
        if (!token.user.enabled) throw AccountDisabledException("This account has been disabled")
        token.revokedAt = Instant.now()
        return issueTokenPair(token.user, deviceInfo ?: token.deviceInfo)
    }

    @Transactional
    fun logout(refreshToken: String, email: String) {
        val token = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
            .orElseThrow { InvalidTokenException("Refresh token is invalid") }
        if (token.user.getEmail() != email) throw InvalidTokenException("Refresh token does not belong to this user")
        token.revokedAt = Instant.now()
    }

    @Transactional
    fun logoutAll(email: String) {
        val user = userRepository.findByEmail(email).orElseThrow { UserNotFoundException("User not found") }
        val now = Instant.now()
        refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user).forEach { it.revokedAt = now }
    }

    private fun issueTokenPair(user: User, deviceInfo: String?): TokenResponse {
        val rawRefreshToken = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        refreshTokenRepository.save(
            RefreshToken(
                tokenHash = hashToken(rawRefreshToken),
                user = user,
                expiryDate = Instant.now().plusMillis(refreshExpirationTime),
                deviceInfo = deviceInfo?.take(255)
            )
        )
        return TokenResponse(
            accessToken = jwtService.generateToken(user),
            refreshToken = rawRefreshToken,
            expiresIn = accessExpirationTime / 1000,
            email = user.getEmail(),
            name = user.getName(),
            roles = user.roles.map { it.name }.sorted()
        )
    }

    private fun hashToken(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
