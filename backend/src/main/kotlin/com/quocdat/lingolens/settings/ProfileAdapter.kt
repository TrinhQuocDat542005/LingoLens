package com.quocdat.lingolens.settings

import com.quocdat.lingolens.common.UserNotFoundException
import com.quocdat.lingolens.user.UpdateProfileRequest
import com.quocdat.lingolens.user.User
import com.quocdat.lingolens.user.UserProfileResponse
import com.quocdat.lingolens.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.jdbc.core.JdbcTemplate
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ProfileAdapter(private val settings: LearnerSettingsRepository, private val users: UserRepository,
    private val jdbc: JdbcTemplate, private val entities: EntityManager) {
    @Transactional
    fun initialize(user: User) {
        val id = requireNotNull(user.id)
        check(!settings.existsById(id)) { "Settings already exist" }
        settings.saveAndFlush(LearnerSettings(id, targetLevel = user.targetLevel, dailyNewWordGoal = user.dailyGoal))
    }

    @Transactional(readOnly = true)
    fun read(user: User): UserProfileResponse {
        val source = settings.findById(requireNotNull(user.id)).orElseThrow { IllegalStateException("Learner settings missing") }
        return UserProfileResponse.from(user).copy(targetLevel = source.targetLevel, dailyGoal = source.dailyNewWordGoal)
    }

    @Transactional
    fun update(userId: Long, request: UpdateProfileRequest): UserProfileResponse {
        require(request.targetLevel in setOf("A1", "B1", "B2") && request.dailyGoal in 1..50)
        // Write every requested value, including values equal to a stale snapshot.
        // Only non-key columns change: PostgreSQL's implicit UPDATE lock allows FK key-share.
        // Always users -> settings, in one transaction. No explicit pessimistic lock.
        val now = java.sql.Timestamp.from(Instant.now())
        if (jdbc.update("UPDATE users SET name=?,target_level=?,daily_goal=?,updated_at=? WHERE id=?",
                request.name.trim(), request.targetLevel, request.dailyGoal, now, userId) != 1) {
            throw UserNotFoundException("User not found")
        }
        check(jdbc.update("UPDATE learner_settings SET target_level=?,daily_new_word_goal=?,updated_at=? WHERE user_id=?",
            request.targetLevel, request.dailyGoal, now, userId) == 1) { "Learner settings missing" }
        val user = users.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        entities.refresh(user)
        settings.findById(userId).ifPresent { entities.refresh(it) }
        return read(user)
    }
}
