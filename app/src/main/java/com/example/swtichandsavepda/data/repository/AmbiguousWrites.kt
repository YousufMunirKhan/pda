package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.remote.PdaApiException

/**
 * Resolves an ambiguous create against the portal's own list, so callers get a
 * definite answer wherever one is obtainable.
 *
 * The three outcomes map back onto [Result] as:
 *  - **Created** → `success`. The write landed; the caller shows it as created.
 *  - **NotCreated** → `failure(Network)` with copy that explicitly invites a
 *    retry. This is the *only* post-send failure that is safe to retry, because
 *    the portal has told us it holds nothing.
 *  - **Inconclusive** → the original [PdaApiException.Ambiguous] survives. The
 *    caller must not retry; it shows the unresolved state instead.
 *
 * Anything that is not ambiguous passes through untouched — a 422 is still a
 * 422.
 */
internal suspend fun <T> Result<T>.resolveIfAmbiguous(
    list: suspend () -> Result<List<T>>,
    matches: (T) -> Boolean,
): Result<T> {
    val ambiguous = exceptionOrNull() as? PdaApiException.Ambiguous ?: return this

    return when (val outcome = reconcileCreate(list, matches)) {
        is Reconciliation.Created -> Result.success(outcome.document)

        Reconciliation.NotCreated -> Result.failure(
            PdaApiException.Network(
                "That didn't reach the server. Nothing was created — safe to try again.",
                ambiguous,
            ),
        )

        Reconciliation.Inconclusive -> this
    }
}
