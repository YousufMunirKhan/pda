package com.example.swtichandsavepda.presentation.screens.credentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CredentialsUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    /** User-facing message for the error banner (already safe to display). */
    val bannerMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

@HiltViewModel
class CredentialsViewModel @Inject constructor(
    private val authRepository: PdaAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CredentialsUiState())
    val uiState: StateFlow<CredentialsUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, emailError = false, bannerMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, passwordError = false, bannerMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun signIn(onSignedIn: () -> Unit) {
        val state = _uiState.value
        val emailBlank = state.email.isBlank()
        val passwordBlank = state.password.isBlank()
        if (emailBlank || passwordBlank) {
            _uiState.update {
                it.copy(
                    emailError = emailBlank,
                    passwordError = passwordBlank,
                    bannerMessage = "Enter your email and password to sign in.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, bannerMessage = null) }

            authRepository.login(state.email, state.password)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, bannerMessage = null) }
                    onSignedIn()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            // Highlight both fields on a credential rejection.
                            emailError = throwable is PdaApiException.Unauthorized,
                            passwordError = throwable is PdaApiException.Unauthorized,
                            bannerMessage = throwable.toUserMessage(),
                        )
                    }
                }
        }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is PdaApiException -> message ?: FALLBACK_MESSAGE
        else -> FALLBACK_MESSAGE
    }

    private companion object {
        const val FALLBACK_MESSAGE = "Could not sign in. Please try again."
    }
}
