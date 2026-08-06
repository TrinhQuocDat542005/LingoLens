package com.quocdat.lingolens.admin

import com.quocdat.lingolens.security.RoleRepository
import com.quocdat.lingolens.user.User
import com.quocdat.lingolens.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminBootstrap(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val encoder: PasswordEncoder,
    @Value("\${app.admin.bootstrap.email:}") private val email: String,
    @Value("\${app.admin.bootstrap.password:}") private val password: String
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (email.isBlank() || password.isBlank()) return
        require(password.length >= 12) { "ADMIN_PASSWORD must contain at least 12 characters" }
        val adminRole = roles.findByName("ROLE_ADMIN").orElseThrow { IllegalStateException("ROLE_ADMIN is missing") }
        val userRole = roles.findByName("ROLE_USER").orElseThrow { IllegalStateException("ROLE_USER is missing") }
        val existing = users.findByEmail(email.trim().lowercase())
        if (existing.isPresent) {
            existing.get().apply {
                roles.add(userRole)
                roles.add(adminRole)
                updatePassword(encoder.encode(password))
                enabled = true
            }
        } else {
            users.save(User(email = email.trim().lowercase(), password = encoder.encode(password), name = "LingoLens Admin", roles = mutableSetOf(userRole, adminRole)))
        }
    }
}
