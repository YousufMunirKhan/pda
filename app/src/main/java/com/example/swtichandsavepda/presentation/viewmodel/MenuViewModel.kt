package com.example.swtichandsavepda.presentation.screens.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.repository.PdaAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val authRepository: PdaAuthRepository,
) : ViewModel() {

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
