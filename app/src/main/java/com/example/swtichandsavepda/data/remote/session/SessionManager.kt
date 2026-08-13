package com.example.swtichandsavepda.data.remote.session

import com.example.swtichandsavepda.data.model.AuthState
import com.example.swtichandsavepda.data.model.AuthUser
import com.example.swtichandsavepda.data.model.Tenant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of "who is signed in". The [AuthInterceptor] reads the token
 * from here for every request; the navigation graph observes [authState] to
 * decide between the app and the login screen.
 *
 * The token is cached in memory (so the very first request already carries it)
 * and mirrored to the encrypted [SecureSessionStore] for persistence.
 */
@Singleton
class SessionManager @Inject constructor(
    private val store: SecureSessionStore,
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @Volatile
    private var cachedToken: String? = null

    init {
        // Preload the persisted token so a request fired before bootstrap()
        // completes is still authenticated. State stays Unknown until /me
        // confirms the token is live.
        cachedToken = store.load()?.token
    }

    fun currentToken(): String? = cachedToken

    /** The last persisted session, used by bootstrap to restore the operator. */
    fun persistedSession(): StoredSession? = store.load()

    /** Called after a successful login (or a validated bootstrap). */
    fun onSignedIn(token: String, user: AuthUser, tenant: Tenant?) {
        cachedToken = token
        store.save(
            StoredSession(
                token = token,
                userId = user.id,
                userName = user.name,
                userEmail = user.email,
                shopId = user.shopId,
                tenantId = tenant?.id,
                tenantName = tenant?.name,
            ),
        )
        _authState.value = AuthState.Authenticated(user, tenant)
    }

    /** Restores auth state from a persisted session without re-hitting the API. */
    fun restoreAuthenticated(session: StoredSession) {
        cachedToken = session.token
        _authState.value = AuthState.Authenticated(
            user = AuthUser(session.userId, session.userName, session.userEmail, session.shopId),
            tenant = Tenant(session.tenantId, session.tenantName),
        )
    }

    /**
     * Clears everything and moves to [AuthState.Unauthenticated]. Called on
     * logout and whenever an authenticated call comes back 401 (the token is
     * dead and PDA has no refresh, so the only recovery is to sign in again).
     */
    fun clear() {
        cachedToken = null
        store.clear()
        _authState.value = AuthState.Unauthenticated
    }

    /** Moves out of [AuthState.Unknown] to Unauthenticated with no stored token. */
    fun markUnauthenticated() {
        _authState.value = AuthState.Unauthenticated
    }
}
