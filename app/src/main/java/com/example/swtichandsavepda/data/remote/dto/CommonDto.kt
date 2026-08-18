package com.example.swtichandsavepda.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Envelope every create/edit/receive/cancel call returns:
 * `{ "success": true, "message": "…", "type": "purchase_order", "data": { … } }`.
 */
@Serializable
data class DocumentEnvelope<T>(
    val success: Boolean = false,
    val message: String? = null,
    val type: String? = null,
    val data: T? = null,
)

/**
 * The shape a validation/authly rejected call returns. Covers both the PDA
 * convention `{ "success": false, "message": "…" }` and Laravel's default
 * `{ "message": "…", "errors": { "field": ["…"] } }`.
 */
@Serializable
data class ApiErrorBody(
    val success: Boolean? = null,
    val message: String? = null,
    val errors: Map<String, List<String>>? = null,
    /**
     * Machine-readable reason, e.g. `OVER_RECEIPT`, `PO_CANCELLED`.
     *
     * Two spellings are parsed because the portal team announced the codes
     * without pinning the envelope key, and `ignoreUnknownKeys` means the wrong
     * guess would be dropped silently rather than failing loudly.
     * NEEDS VERIFICATION: confirm which key the portal actually sends.
     */
    val code: String? = null,
    val errorCode: String? = null,
) {
    val failureCode: String? get() = code ?: errorCode
}
