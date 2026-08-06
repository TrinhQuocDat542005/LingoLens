package com.quocdat.lingolens.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun countByEnabledTrue(): Long
    fun countByEnabledFalse(): Long
    fun countByCreatedAtAfter(since: Instant): Long

    @Query("select count(distinct u) from User u join u.roles r where r.name = :role")
    fun countByRole(@Param("role") role: String): Long

    @Query(
        """select distinct u from User u left join u.roles r
           where (:search = '' or lower(u.email) like lower(concat('%', :search, '%'))
              or lower(u.name) like lower(concat('%', :search, '%')))
           and (:enabled is null or u.enabled = :enabled)
           and (:role = '' or r.name = :role)"""
    )
    fun searchAdminUsers(
        @Param("search") search: String,
        @Param("enabled") enabled: Boolean?,
        @Param("role") role: String,
        pageable: Pageable
    ): Page<User>
}
