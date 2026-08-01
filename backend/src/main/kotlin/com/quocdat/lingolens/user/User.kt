package com.quocdat.lingolens.user

import com.quocdat.lingolens.security.Role
import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

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
    fun updateProfile(name: String, targetLevel: String, dailyGoal: Int) {
        this.name = name
        this.targetLevel = targetLevel
        this.dailyGoal = dailyGoal
    }

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = enabled
}
