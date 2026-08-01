package com.quocdat.lingolens.user

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val name: String,
    val targetLevel: String,
    val streakDays: Int,
    val dailyGoal: Int,
    val roles: List<String>
) {
    companion object {
        fun from(user: User) = UserProfileResponse(
            id = requireNotNull(user.id),
            email = user.getEmail(),
            name = user.getName(),
            targetLevel = user.targetLevel,
            streakDays = user.streakDays,
            dailyGoal = user.dailyGoal,
            roles = user.roles.map { it.name }.sorted()
        )
    }
}
