package com.quocdat.lingolens.user

import com.quocdat.lingolens.security.Role
import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true, nullable = false)
    private var email: String,

    @Column(nullable = false)
    private var password: String,

    @Column(nullable = false)
    private var name: String,

    @Column(name = "target_level", nullable = false)
    var targetLevel: String = "B1",

    @Column(name = "streak_days", nullable = false)
    var streakDays: Int = 0,

    @Column(name = "daily_goal", nullable = false)
    var dailyGoal: Int = 5,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf()
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return roles.map { SimpleGrantedAuthority(it.name) }
    }

    override fun getPassword(): String = password
    override fun getUsername(): String = email
    
    fun getEmail(): String = email
    fun getName(): String = name
    fun updatePassword(encodedPassword: String) {
        password = encodedPassword
        updatedAt = Instant.now()
    }
    fun updateProfile(name: String, targetLevel: String, dailyGoal: Int) {
        this.name = name
        this.targetLevel = targetLevel
        this.dailyGoal = dailyGoal
        this.updatedAt = Instant.now()
    }

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = enabled
}
