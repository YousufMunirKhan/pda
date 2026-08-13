package com.example.swtichandsavepda.presentation.screens.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.local.SubmissionAttempt
import com.example.swtichandsavepda.data.local.SubmissionJournal
import com.example.swtichandsavepda.data.repository.PdaAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MenuUiState(
    /** Writes that were sent but never confirmed. Empty in the normal case. */
    val unresolved: List<SubmissionAttempt> = emptyList(),
)

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val authRepository: PdaAuthRepository,
    private val journal: SubmissionJournal,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    init {
        refreshUnresolved()
    }

    /**
     * The operator's reconciliation report. A write that was sent but never
     * confirmed — including one whose process died mid-flight — surfaces here so
     * it is checked rather than silently re-keyed.
     */
    fun refreshUnresolved() {
        viewModelScope.launch {
            _uiState.value = MenuUiState(unresolved = journal.unresolved())
        }
    }

    /** The operator has checked this one against the portal. */
    fun acknowledge(attemptId: String) {
        viewModelScope.launch {
            journal.forget(attemptId)
            refreshUnresolved()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            // Best-effort revoke; the repository clears the local session even if
            // the call fails, so a failed logout never strands the operator on an
            // authenticated screen.
            authRepository.logout()
            onLoggedOut()
        }
    }
}
