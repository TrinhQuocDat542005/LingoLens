package com.quocdat.lingolens.data.local

import com.quocdat.lingolens.data.remote.dto.TokenDto
import com.quocdat.lingolens.data.remote.dto.UserProfileDto

interface CredentialStore {
    suspend fun current(): StoredSession?
    suspend fun cachedProfile(): UserProfileDto?
    suspend fun saveRefreshed(expected: StoredSession, tokens: TokenDto): Boolean
    suspend fun clearIfCurrent(expected: StoredSession)
}
