package com.example.swtichandsavepda.data.remote

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One attempt at a non-idempotent write (create / edit / receive / cancel).
 *
 * The whole point is [requestFullySent]. An `IOException` on a POST means two
 * completely different things depending on whether the request body already left
 * the device:
 *
 *  - **not sent** — the portal never saw it. Safe to retry.
 *  - **sent** — the portal may well have committed the document and only the
 *    *answer* was lost. Retrying here is how one goods-in becomes two.
 *
 * OkHttp does not expose that distinction on the exception, so we observe it via
 * [WriteAttemptEventListenerFactory] and carry the answer here.
 *
 * Attach one instance per call as an OkHttp tag; a `WriteAttempt` is single-use.
 */
class WriteAttempt(val startedAtEpochMs: Long = System.currentTimeMillis()) {

    private val sent = AtomicBoolean(false)

    /** True once the request body has left this device. */
    val requestFullySent: Boolean get() = sent.get()

    internal fun markSent() {
        sent.set(true)
    }
}

/**
 * Flips [WriteAttempt.markSent] the moment OkHttp finishes writing the request.
 * Calls with no [WriteAttempt] tag (every GET) are ignored outright.
 */
object WriteAttemptEventListenerFactory : EventListener.Factory {

    override fun create(call: Call): EventListener {
        val attempt = call.request().tag(WriteAttempt::class.java) ?: return EventListener.NONE

        return object : EventListener() {
            override fun requestBodyEnd(call: Call, byteCount: Long) {
                attempt.markSent()
            }

            /** A bodyless write (the cancel/receive POSTs) is sent once its headers are. */
            override fun requestHeadersEnd(call: Call, request: Request) {
                if (request.body == null) attempt.markSent()
            }
        }
    }
}
