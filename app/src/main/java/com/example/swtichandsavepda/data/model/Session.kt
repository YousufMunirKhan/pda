package com.example.swtichandsavepda.data.model

/** The signed-in PDA operator, as returned by login / `me`. */
data class AuthUser(
    val id: Long,
    val name: String,
    val email: String,
    val shopId: Long?,
)

/** The tenant (store) the operator belongs to. */
data class Tenant(
    val id: String?,
    val name: String?,
)

/**
 * Where the app is in the auth lifecycle. [Unknown] is the pre-bootstrap state
 * while a stored token is being validated, so the UI can hold on a splash
 * instead of flashing the login screen for already-signed-in users.
 */
sealed interface AuthState {
    data object Unknown : AuthState
    data class Authenticated(val user: AuthUser, val tenant: Tenant?) : AuthState
    data object Unauthenticated : AuthState
}
