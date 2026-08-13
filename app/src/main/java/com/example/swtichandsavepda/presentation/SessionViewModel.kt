package com.example.swtichandsavepda.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.model.AuthState
import com.example.swtichandsavepda.data.repository.PdaAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-scoped auth state, hoisted at the navigation host so a single instance
 * drives both the initial splash routing and mid-session forced logout (when an
 * authenticated call comes back 401 and [SessionManager] clears the session).
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: PdaAuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    init {
        // Validate any persisted token exactly once at startup.
        viewModelScope.launch { authRepository.bootstrap() }
    }
}
