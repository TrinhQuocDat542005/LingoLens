package com.quocdat.lingolens.auth

import com.quocdat.lingolens.user.User
import com.quocdat.lingolens.user.UserRepository
import com.quocdat.lingolens.security.RoleRepository
import com.quocdat.lingolens.security.RefreshToken
import com.quocdat.lingolens.security.RefreshTokenRepository
import com.quocdat.lingolens.security.JwtService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    @Value("\${security.jwt.refresh-expiration-time}")
    private val refreshExpirationTime: Long
) {

    @Transactional
    fun register(request: RegisterRequest): User {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email is already registered")
        }

        val userRole = roleRepository.findByName("ROLE_USER")
            .orElseThrow { IllegalStateException("Default role ROLE_USER not found") }

        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = request.name,
            roles = setOf(userRole)
        )

        return userRepository.save(user)
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        val user = userRepository.findByEmail(request.email)
            .orElseThrow { IllegalArgumentException("Invalid email or password") }

        val accessToken = jwtService.generateToken(user)
        
        refreshTokenRepository.deleteByUser(user)
        
        val refreshToken = createRefreshToken(user)

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken.token,
            email = user.getEmail(),
            name = user.getName(),
            roles = user.roles.map { it.name }
        )
    }

    @Transactional
    fun refresh(request: RefreshTokenRequest): TokenResponse {
        val token = refreshTokenRepository.findByToken(request.refreshToken)
            .orElseThrow { IllegalArgumentException("Refresh token not found") }

        if (token.expiryDate.isBefore(Instant.now())) {
            refreshTokenRepository.delete(token)
            throw IllegalArgumentException("Refresh token was expired. Please sign in again")
        }

        val user = token.user
        val accessToken = jwtService.generateToken(user)
        
        refreshTokenRepository.delete(token)
        val newRefreshToken = createRefreshToken(user)

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = newRefreshToken.token,
            email = user.getEmail(),
            name = user.getName(),
            roles = user.roles.map { it.name }
        )
    }

    @Transactional
    fun logout(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { IllegalArgumentException("User not found") }
        refreshTokenRepository.deleteByUser(user)
    }

    private fun createRefreshToken(user: User): RefreshToken {
        val refreshToken = RefreshToken(
            token = UUID.randomUUID().toString(),
            user = user,
            expiryDate = Instant.now().plusMillis(refreshExpirationTime)
        )
        return refreshTokenRepository.save(refreshToken)
    }
}
