package com.example.swtichandsavepda.data.remote.dto

import kotlinx.serialization.Serializable

/** Body for `POST /api/pda/login`. */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

/** Response for `POST /api/pda/login`. */
@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val token: String? = null,
    val tokenType: String? = null,
    val user: PdaUserDto? = null,
    val tenant: TenantDto? = null,
)

/**
 * `GET /api/pda/me`. The exact envelope is undocumented, so this tolerates both
 * a wrapped `{ "user": { … } }` and a bare user object (top-level id/name/…).
 */
@Serializable
data class MeResponse(
    val success: Boolean? = null,
    val user: PdaUserDto? = null,
    val tenant: TenantDto? = null,
    // Fallback when the endpoint returns the user object directly.
    val id: Long? = null,
    val name: String? = null,
    val email: String? = null,
    val shopId: Long? = null,
) {
    /** The user whichever way the server shaped the response. */
    fun resolveUser(): PdaUserDto? = user ?: id?.let {
        PdaUserDto(id = it, name = name.orEmpty(), email = email.orEmpty(), shopId = shopId)
    }
}

@Serializable
data class PdaUserDto(
    val id: Long,
    val name: String = "",
    val email: String = "",
    val shopId: Long? = null,
)

@Serializable
data class TenantDto(
    val id: String? = null,
    val name: String? = null,
)

/** Generic `{ "success": …, "message": … }` used by logout and similar. */
@Serializable
data class SimpleResponse(
    val success: Boolean = false,
    val message: String? = null,
)
