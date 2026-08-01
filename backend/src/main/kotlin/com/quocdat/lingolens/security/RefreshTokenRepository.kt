package com.quocdat.lingolens.security

import com.quocdat.lingolens.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>
    fun findAllByUserAndRevokedAtIsNull(user: User): List<RefreshToken>
}
