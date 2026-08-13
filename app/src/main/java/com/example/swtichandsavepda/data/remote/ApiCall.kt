package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.remote.dto.ApiErrorBody
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs an **idempotent** call (every GET) and folds it into a [Result],
 * translating transport and HTTP failures into the typed [PdaApiException]
 * hierarchy. Re-running a read is free, so every failure is simply a failure.
 *
 * Non-idempotent calls must use [safeWriteCall] instead — see its comment.
 *
 * [CancellationException] is deliberately not caught (it is not an
 * [IOException]/[HttpException]/[SerializationException]), so coroutine
 * cancellation still propagates.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (httpError: HttpException) {
    Result.failure(httpError.toPdaException())
} catch (networkError: IOException) {
    Result.failure(
        PdaApiException.Network(
            "Can't reach the server. Check your connection and try again.",
            networkError,
        ),
    )
} catch (parseError: SerializationException) {
    Result.failure(
        PdaApiException.Unexpected("The server sent an unexpected response.", parseError),
    )
}

/**
 * Runs a **non-idempotent** call — the create / edit / receive / cancel writes —
 * and folds it into a [Result].
 *
 * The difference from [safeApiCall] is that a failure arriving *after* the
 * request body was sent is [PdaApiException.Ambiguous], not
 * [PdaApiException.Network] or [PdaApiException.Server]. The portal may already
 * hold the document, so telling the operator "try again" would be inviting a
 * duplicate stock movement.
 *
 * Classification:
 *  - **4xx** — a pre-write rejection (validation, auth, not-found). Clean failure.
 *  - **5xx after sending** — Laravel may have committed the row before the
 *    handler blew up. Ambiguous.
 *  - **IOException after sending** — read timeout / dropped return path. Ambiguous.
 *  - **IOException before sending** — never reached the portal. Clean failure,
 *    and the only case whose message invites a retry.
 *  - **Unreadable 2xx** — the portal accepted it and we could not read the
 *    answer. Ambiguous, never a failure.
 */
suspend fun <T> safeWriteCall(
    attempt: WriteAttempt,
    block: suspend () -> T,
): Result<T> = try {
    Result.success(block())
} catch (httpError: HttpException) {
    // NEEDS VERIFICATION with the portal team: confirm no 4xx is ever returned
    // after a partial write. If one can be, it belongs in the ambiguous branch.
    if (httpError.code() in 500..599 && attempt.requestFullySent) {
        Result.failure(
            PdaApiException.Ambiguous(
                "The server errored after receiving this. It may already have been created.",
                attempt.startedAtEpochMs,
                httpError,
            ),
        )
    } else {
        Result.failure(httpError.toPdaException())
    }
} catch (networkError: IOException) {
    if (attempt.requestFullySent) {
        Result.failure(
            PdaApiException.Ambiguous(
                "The connection dropped after this was sent. It may already have been created.",
                attempt.startedAtEpochMs,
                networkError,
            ),
        )
    } else {
        Result.failure(
            PdaApiException.Network(
                "Can't reach the server. Nothing was sent — safe to try again.",
                networkError,
            ),
        )
    }
} catch (parseError: SerializationException) {
    Result.failure(
        PdaApiException.Ambiguous(
            "The server accepted this but sent a response we couldn't read.",
            attempt.startedAtEpochMs,
            parseError,
        ),
    )
}

private fun HttpException.toPdaException(): PdaApiException {
    val error = parseErrorBody()
    val message = error?.message?.takeIf { it.isNotBlank() } ?: defaultMessageFor(code())
    return when (code()) {
        401 -> PdaApiException.Unauthorized(message)
        403 -> PdaApiException.Forbidden(message)
        404 -> PdaApiException.NotFound(message)
        422 -> PdaApiException.Validation(message, error?.errors.orEmpty())
        in 500..599 -> PdaApiException.Server(message)
        else -> PdaApiException.Unexpected(message)
    }
}

private fun HttpException.parseErrorBody(): ApiErrorBody? {
    val raw = response()?.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { PdaJson.instance.decodeFromString<ApiErrorBody>(raw) }.getOrNull()
}

private fun defaultMessageFor(code: Int): String = when (code) {
    401 -> "Your session has expired. Please sign in again."
    403 -> "This account is inactive or has no shop assigned."
    404 -> "That record could not be found."
    in 500..599 -> "The server had a problem. Please try again shortly."
    else -> "Something went wrong (HTTP $code)."
}
