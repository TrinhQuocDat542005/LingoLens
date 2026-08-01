package com.quocdat.lingolens.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quocdat.lingolens.data.remote.dto.UserProfileDto
import com.quocdat.lingolens.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val profile: UserProfileDto) : SessionState
}

data class AuthFormState(
    val loading: Boolean = false,
    val error: String? = null,
    val registrationComplete: Boolean = false
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()
    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    init { restore() }

    fun restore() = viewModelScope.launch {
        _session.value = SessionState.Loading
        repository.restoreSession().fold(
            onSuccess = { profile -> _session.value = profile?.let { SessionState.SignedIn(it) } ?: SessionState.SignedOut },
            onFailure = { _session.value = SessionState.SignedOut }
        )
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        val validation = AuthValidator.login(email, password)
        if (validation != null) { _form.value = AuthFormState(error = validation); return@launch }
        _form.value = AuthFormState(loading = true)
        repository.login(email, password).fold(
            onSuccess = { _form.value = AuthFormState(); _session.value = SessionState.SignedIn(it) },
            onFailure = { _form.value = AuthFormState(error = it.message) }
        )
    }

    fun register(name: String, email: String, password: String, confirmation: String) = viewModelScope.launch {
        val validation = AuthValidator.registration(name, email, password, confirmation)
        if (validation != null) { _form.value = AuthFormState(error = validation); return@launch }
        _form.value = AuthFormState(loading = true)
        repository.register(name, email, password).fold(
            onSuccess = { _form.value = AuthFormState(registrationComplete = true) },
            onFailure = { _form.value = AuthFormState(error = it.message) }
        )
    }

    fun updateProfile(name: String, level: String, dailyGoal: Int) = viewModelScope.launch {
        _form.value = AuthFormState(loading = true)
        repository.updateProfile(name, level, dailyGoal).fold(
            onSuccess = { _form.value = AuthFormState(); _session.value = SessionState.SignedIn(it) },
            onFailure = { _form.value = AuthFormState(error = it.message) }
        )
    }

    fun logout() = viewModelScope.launch {
        repository.logout()
        _form.value = AuthFormState()
        _session.value = SessionState.SignedOut
    }

    fun consumeRegistration() { _form.value = AuthFormState() }
    fun clearError() { _form.value = _form.value.copy(error = null) }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
    }
}
