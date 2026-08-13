package com.example.swtichandsavepda.data.remote

/**
 * Typed failures the repositories surface, so ViewModels can react to the kind
 * of error (re-auth, show field errors, offer retry) without touching Retrofit
 * or HTTP status codes. Messages are already user-safe — parsed from the API's
 * `message`, never a raw stack trace.
 */
sealed class PdaApiException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** 401 — token missing/expired/revoked, or wrong credentials on login. */
    class Unauthorized(message: String) : PdaApiException(message)

    /** 403 — inactive account or no shop assigned. */
    class Forbidden(message: String) : PdaApiException(message)

    /** 404 — the document is not on this shop (or was removed). */
    class NotFound(message: String) : PdaApiException(message)

    /** 422 — soft validation failed; [fieldErrors] maps field → messages. */
    class Validation(
        message: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
    ) : PdaApiException(message)

    /** 5xx — the portal errored. */
    class Server(message: String) : PdaApiException(message)

    /** No/failed connection (timeouts, DNS, offline) **before anything was sent**. */
    class Network(message: String, cause: Throwable? = null) : PdaApiException(message, cause)

    /**
     * The write left this device but no usable answer came back — a read timeout,
     * a 5xx, or a 2xx we could not read. The document may or may not exist on the
     * portal.
     *
     * **Never auto-retry an [Ambiguous] write.** Creating a purchase order,
     * return or adjustment is not idempotent, so a blind retry is how one
     * goods-in becomes two. Resolve it by asking the portal what it actually
     * holds — see `reconcileCreate`.
     */
    class Ambiguous(
        message: String,
        val attemptStartedAtEpochMs: Long,
        cause: Throwable? = null,
    ) : PdaApiException(message, cause)

    /** Anything else — an unexpected status or an unparseable body. */
    class Unexpected(message: String, cause: Throwable? = null) : PdaApiException(message, cause)
}
