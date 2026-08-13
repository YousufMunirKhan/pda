package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.remote.dto.ApiErrorBody
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a Retrofit call and folds it into a [Result], translating transport and
 * HTTP failures into the typed [PdaApiException] hierarchy.
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
