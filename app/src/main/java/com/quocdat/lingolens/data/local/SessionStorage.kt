package com.quocdat.lingolens.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quocdat.lingolens.data.remote.dto.TokenDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore("secure_session")

data class StoredSession(val accessToken: String, val refreshToken: String, val email: String, val name: String,
    val generation: String? = null)

class SessionStorage(private val context: Context, private val cipher: SessionTokenCipher = TokenCipher()) : CredentialStore {
    private object Keys {
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val email = stringPreferencesKey("user_email")
        val name = stringPreferencesKey("user_name")
        val profile = stringPreferencesKey("cached_profile")
        val generation = stringPreferencesKey("login_generation")
    }

    val session: Flow<StoredSession?> = context.sessionDataStore.data.map { prefs ->
        val access = prefs[Keys.access]?.let(cipher::decrypt)
        val refresh = prefs[Keys.refresh]?.let(cipher::decrypt)
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) null else StoredSession(
            accessToken = access,
            refreshToken = refresh,
            email = prefs[Keys.email].orEmpty(),
            name = prefs[Keys.name].orEmpty(),
            generation = prefs[Keys.generation]
        )
    }

    override suspend fun current(): StoredSession? = session.first()

    suspend fun save(tokens: TokenDto) {
        context.sessionDataStore.edit {
            // A new login may be a new identity even when the email is unchanged.
            it.remove(Keys.profile)
            it[Keys.generation] = java.util.UUID.randomUUID().toString()
            it[Keys.access] = cipher.encrypt(tokens.accessToken)
            it[Keys.refresh] = cipher.encrypt(tokens.refreshToken)
            it[Keys.email] = tokens.email
            it[Keys.name] = tokens.name
        }
    }

    suspend fun saveProfile(profile: com.quocdat.lingolens.data.remote.dto.UserProfileDto, expected: StoredSession) {
        context.sessionDataStore.edit {
            // Refresh keeps the generation; a stale response from a prior login cannot overwrite it.
            if (it[Keys.email] == profile.email && it[Keys.email] == expected.email &&
                it[Keys.generation] == expected.generation) it[Keys.profile] = com.google.gson.Gson().toJson(profile)
        }
    }

    override suspend fun cachedProfile(): com.quocdat.lingolens.data.remote.dto.UserProfileDto? {
        val prefs = context.sessionDataStore.data.first()
        return prefs[Keys.profile]?.let {
            runCatching { com.google.gson.Gson().fromJson(it, com.quocdat.lingolens.data.remote.dto.UserProfileDto::class.java) }.getOrNull()
        }?.takeIf { it.email == prefs[Keys.email] }
    }

    suspend fun clear() = context.sessionDataStore.edit { it.clear() }

    override suspend fun saveRefreshed(expected: StoredSession, tokens: TokenDto): Boolean {
        var saved = false
        context.sessionDataStore.edit {
            if (it[Keys.refresh]?.let(cipher::decrypt) == expected.refreshToken && it[Keys.email] == expected.email) {
                require(tokens.email == expected.email)
                it[Keys.access] = cipher.encrypt(tokens.accessToken)
                it[Keys.refresh] = cipher.encrypt(tokens.refreshToken)
                saved = true
            }
        }
        return saved
    }
    override suspend fun clearIfCurrent(expected: StoredSession) {
        context.sessionDataStore.edit {
            if (it[Keys.refresh]?.let(cipher::decrypt) == expected.refreshToken && it[Keys.email] == expected.email) it.clear()
        }
    }
}
