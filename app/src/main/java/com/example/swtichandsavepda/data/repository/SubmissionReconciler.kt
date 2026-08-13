package com.example.swtichandsavepda.data.repository

/**
 * What a reconciliation pass concluded about an ambiguous write.
 *
 * There are deliberately three outcomes, not two. Collapsing [Inconclusive] into
 * [NotCreated] is exactly the mistake that produces a duplicate: "we couldn't
 * check" is not "it isn't there".
 */
sealed interface Reconciliation<out T> {

    /** The portal holds it. The write succeeded; do not send again. */
    data class Created<T>(val document: T) : Reconciliation<T>

    /** The portal answered and does not hold it. Genuinely safe to retry. */
    data object NotCreated : Reconciliation<Nothing>

    /** Could not reach the portal to check. Still unknown — do not retry. */
    data object Inconclusive : Reconciliation<Nothing>
}

/**
 * Resolves an ambiguous create by asking the portal what it actually holds.
 *
 * This is the stock equivalent of a payment status enquiry: after an ambiguous
 * write we never re-send, we ask. [list] is the document type's existing `GET`
 * — no new portal endpoint is needed — and [matches] identifies the document
 * this attempt would have produced.
 *
 * Matching is a heuristic because the portal echoes no client reference
 * (NEEDS VERIFICATION: if it can be made to accept and echo one, replace
 * [matches] with an exact lookup and delete the created-at window). Purchase
 * returns are the exception — the operator supplies `reference_no`, which is a
 * real business key, so their matcher is exact.
 */
suspend fun <T> reconcileCreate(
    list: suspend () -> Result<List<T>>,
    matches: (T) -> Boolean,
): Reconciliation<T> = list().fold(
    onSuccess = { documents ->
        documents.firstOrNull(matches)
            ?.let { Reconciliation.Created(it) }
            ?: Reconciliation.NotCreated
    },
    onFailure = { Reconciliation.Inconclusive },
)

/**
 * Allows for the PDA's clock running ahead of the portal's when comparing a
 * document's `created_at` against the moment we started the attempt. Two minutes
 * is generous for handhelds that sync time over the network.
 */
const val CLOCK_SKEW_MS: Long = 2 * 60 * 1000L

/** Quantity comparison for matching — portal decimals round-trip through strings. */
fun Double.isCloseTo(other: Double, tolerance: Double = 0.0001): Boolean =
    kotlin.math.abs(this - other) < tolerance
