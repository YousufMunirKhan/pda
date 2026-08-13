package com.example.swtichandsavepda.data.remote.interceptor

import com.example.swtichandsavepda.data.remote.session.SessionManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles a 401 on an already-authenticated call. PDA issues no refresh token,
 * so a dead token cannot be renewed — we clear the session and give up
 * (returning null). The navigation graph observes [SessionManager.authState]
 * and routes back to login.
 *
 * Guarded on the presence of an Authorization header so a 401 from the public
 * login endpoint (bad credentials) does not wipe an unrelated session.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val wasAuthenticated = response.request.header("Authorization") != null
        if (wasAuthenticated) {
            sessionManager.clear()
        }
        return null
    }
}
