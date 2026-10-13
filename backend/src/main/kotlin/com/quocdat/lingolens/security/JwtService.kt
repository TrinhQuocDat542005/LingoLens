package com.quocdat.lingolens.security

import com.quocdat.lingolens.user.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.Date
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${security.jwt.secret-key}")
    private val secretKeyString: String,

    @Value("\${security.jwt.expiration-time}")
    private val jwtExpiration: Long
) {

    // Validate key length at startup rather than failing during the first login.
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(secretKeyString.toByteArray(StandardCharsets.UTF_8))

    fun extractUsername(token: String): String {
        return extractClaim(token, Claims::getSubject)
    }

    fun extractUserId(token: String): Long {
        val value = extractClaim(token) { it["userId"] }
        require(value is Int || value is Long) { "JWT userId must be an integer" }
        return (value as Number).toLong().also { require(it > 0) { "JWT userId must be positive" } }
    }

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    fun generateToken(userDetails: UserDetails): String {
        return generateToken(HashMap(), userDetails)
    }

    fun generateToken(extraClaims: Map<String, Any>, userDetails: UserDetails): String {
        require(userDetails is User && userDetails.id != null && userDetails.id!! > 0) { "Persisted user identity required" }
        val claimsBuilder = Jwts.claims().subject(userDetails.username)
        extraClaims.forEach { (key, value) -> claimsBuilder.add(key, value) }
        
        if (userDetails is User) {
            // Identity is authoritative, never supplied by extraClaims or derived from an email.
            claimsBuilder.add("userId", userDetails.id!!)
            claimsBuilder.add("roles", userDetails.roles.map { it.name })
            claimsBuilder.add("name", userDetails.getName())
        }

        return Jwts.builder()
            .claims(claimsBuilder.build())
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(signingKey)
            .compact()
    }

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return userDetails is User && extractUserId(token) == userDetails.id &&
            (username == userDetails.username) && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        return extractExpiration(token).before(Date())
    }

    private fun extractExpiration(token: String): Date {
        return extractClaim(token, Claims::getExpiration)
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
