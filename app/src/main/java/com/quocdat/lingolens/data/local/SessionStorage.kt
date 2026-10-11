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

data class StoredSession(val accessToken: String, val refreshToken: String, val email: String, val name: String)

class SessionStorage(private val context: Context) {
    private val cipher = TokenCipher()
    private object Keys {
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val email = stringPreferencesKey("user_email")
        val name = stringPreferencesKey("user_name")
        val profile = stringPreferencesKey("cached_profile")
    }

    val session: Flow<StoredSession?> = context.sessionDataStore.data.map { prefs ->
        val access = prefs[Keys.access]?.let(cipher::decrypt)
        val refresh = prefs[Keys.refresh]?.let(cipher::decrypt)
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) null else StoredSession(
            accessToken = access,
            refreshToken = refresh,
            email = prefs[Keys.email].orEmpty(),
            name = prefs[Keys.name].orEmpty()
        )
    }

    suspend fun current(): StoredSession? = session.first()

    suspend fun save(tokens: TokenDto) {
        context.sessionDataStore.edit {
            if (it[Keys.email] != tokens.email) it.remove(Keys.profile)
            it[Keys.access] = cipher.encrypt(tokens.accessToken)
            it[Keys.refresh] = cipher.encrypt(tokens.refreshToken)
            it[Keys.email] = tokens.email
            it[Keys.name] = tokens.name
        }
    }

    suspend fun saveProfile(profile: com.quocdat.lingolens.data.remote.dto.UserProfileDto) {
        context.sessionDataStore.edit {
            if (it[Keys.email] == profile.email) it[Keys.profile] = com.google.gson.Gson().toJson(profile)
        }
    }

    suspend fun cachedProfile(): com.quocdat.lingolens.data.remote.dto.UserProfileDto? {
        val prefs = context.sessionDataStore.data.first()
        return prefs[Keys.profile]?.let {
            runCatching { com.google.gson.Gson().fromJson(it, com.quocdat.lingolens.data.remote.dto.UserProfileDto::class.java) }.getOrNull()
        }?.takeIf { it.email == prefs[Keys.email] }
    }

    suspend fun clear() = context.sessionDataStore.edit { it.clear() }
}
