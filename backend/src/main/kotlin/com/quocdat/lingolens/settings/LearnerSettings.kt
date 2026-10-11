package com.quocdat.lingolens.settings

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@Entity
@Table(name = "learner_settings")
class LearnerSettings(
    @Id @Column(name = "user_id") var userId: Long,
    @Column(name = "target_level", nullable = false, length = 10) var targetLevel: String = "A1",
    @Column(name = "learning_goal", nullable = false, length = 30) var learningGoal: String = "UNSET",
    @Column(name = "daily_new_word_goal", nullable = false) var dailyNewWordGoal: Int = 5,
    @Column(name = "daily_review_goal", nullable = false) var dailyReviewGoal: Short = 10,
    @Column(length = 64) var timezone: String? = null,
    @Column(name = "onboarding_completed_at") var onboardingCompletedAt: Instant? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)

interface LearnerSettingsRepository : JpaRepository<LearnerSettings, Long>
