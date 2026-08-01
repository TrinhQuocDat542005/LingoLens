package com.quocdat.lingolens.security

import com.quocdat.lingolens.user.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }
        try{
            val jwt = authHeader.substring(7)
            val userEmail = jwtService.extractUsername(jwt)

            if (SecurityContextHolder.getContext().authentication == null) {
                val userDetailsOpt = userRepository.findByEmail(userEmail)
                if (userDetailsOpt.isPresent) {
                    val userDetails = userDetailsOpt.get()
                    if (jwtService.isTokenValid(jwt, userDetails)) {
                        val authToken = UsernamePasswordAuthenticationToken(
                         userDetails,
                            null,
                            userDetails.authorities
                        )
                        authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = authToken
                    }
                }
            }
        } catch (ex: io.jsonwebtoken.ExpiredJwtException) {
            request.setAttribute("exception_code", "TOKEN_EXPIRED")
            request.setAttribute("exception_message", "JWT access token has expired")
        } catch (ex: Exception) {
            request.setAttribute("exception_code", "INVALID_TOKEN")
            request.setAttribute("exception_message", "JWT token is invalid or malformed")
        }

        filterChain.doFilter(request, response)
    }
}
