package com.quocdat.lingolens.ui.learning

import androidx.compose.runtime.*
import com.quocdat.lingolens.AppContainer
import com.quocdat.lingolens.data.remote.dto.UserProfileDto

data class LearningSession(val container: AppContainer, val profile: UserProfileDto)
val LocalLearningSession = staticCompositionLocalOf<LearningSession> { error("Learning session required") }
