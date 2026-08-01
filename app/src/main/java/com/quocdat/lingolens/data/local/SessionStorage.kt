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
            it[Keys.access] = cipher.encrypt(tokens.accessToken)
            it[Keys.refresh] = cipher.encrypt(tokens.refreshToken)
            it[Keys.email] = tokens.email
            it[Keys.name] = tokens.name
        }
    }

    suspend fun clear() = context.sessionDataStore.edit { it.clear() }
}
