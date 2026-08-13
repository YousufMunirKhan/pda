package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.AuthState
import com.example.swtichandsavepda.data.model.AuthUser
import com.example.swtichandsavepda.data.model.Tenant
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.dto.LoginRequest
import com.example.swtichandsavepda.data.remote.session.SessionManager
import com.example.swtichandsavepda.data.remote.safeApiCall
import com.example.swtichandsavepda.data.remote.toDomain
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Auth against the live PDA portal, owning session persistence via [SessionManager]. */
interface PdaAuthRepository {

    /** Observed by the navigation graph to route between app and login. */
    val authState: StateFlow<AuthState>

    /** Exchanges credentials for a token and stores the session. */
    suspend fun login(email: String, password: String): Result<AuthUser>

    /**
     * Validates any persisted token against `/me` on startup, moving the app out
     * of [AuthState.Unknown]. Keeps the user signed in on a transient network
     * failure; only a 401/403 clears the session.
     */
    suspend fun bootstrap()

    /** Best-effort token revoke, then clears the local session unconditionally. */
    suspend fun logout(): Result<Unit>
}

@Singleton
class PdaAuthRepositoryImpl @Inject constructor(
    private val api: PdaApiService,
    private val sessionManager: SessionManager,
) : PdaAuthRepository {

    override val authState: StateFlow<AuthState> = sessionManager.authState

    override suspend fun login(email: String, password: String): Result<AuthUser> =
        safeApiCall { api.login(LoginRequest(email.trim(), password)) }.fold(
            onSuccess = { response ->
                val token = response.token
                val user = response.user?.toDomain()
                if (token.isNullOrBlank() || user == null) {
                    Result.failure(PdaApiException.Unexpected("Sign-in response was incomplete."))
                } else {
                    sessionManager.onSignedIn(token, user, response.tenant?.toDomain())
                    Result.success(user)
                }
            },
            onFailure = { Result.failure(it) },
        )

    override suspend fun bootstrap() {
        val stored = sessionManager.persistedSession()
        if (stored == null) {
            sessionManager.markUnauthenticated()
            return
        }

        safeApiCall { api.me() }.fold(
            onSuccess = { me ->
                val user = me.resolveUser()?.toDomain()
                val tenant = me.tenant?.toDomain() ?: Tenant(stored.tenantId, stored.tenantName)
                if (user != null) {
                    sessionManager.onSignedIn(stored.token, user, tenant)
                } else {
                    // Token is valid but the body was unexpected — trust the
                    // stored session rather than bouncing a signed-in user.
                    sessionManager.restoreAuthenticated(stored)
                }
            },
            onFailure = { throwable ->
                when (throwable) {
                    is PdaApiException.Unauthorized,
                    is PdaApiException.Forbidden,
                    -> sessionManager.clear() // token dead / account inactive
                    else -> sessionManager.restoreAuthenticated(stored) // stay signed in offline
                }
            },
        )
    }

    override suspend fun logout(): Result<Unit> {
        // Revoke server-side if we can, but never leave the user stranded on an
        // authenticated screen if the call fails — always clear locally.
        safeApiCall { api.logout() }
        sessionManager.clear()
        return Result.success(Unit)
    }
}
