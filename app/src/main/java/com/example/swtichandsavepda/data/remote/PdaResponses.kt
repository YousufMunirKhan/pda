package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.remote.dto.DocumentEnvelope
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement

/**
 * Folds a create/edit/receive/cancel envelope into a domain [Result].
 *
 * A 2xx with no `data` means the portal **accepted** the write and we could not
 * read what it produced — which is [PdaApiException.Ambiguous], never a failure.
 * Reporting it as a failure would invite a retry against a document that already
 * exists; the caller resolves it by reconciling instead.
 */
fun <D, R> Result<DocumentEnvelope<D>>.mapDocument(
    attempt: WriteAttempt,
    transform: (D) -> R,
): Result<R> = fold(
    onSuccess = { envelope ->
        envelope.data?.let { Result.success(transform(it)) }
            ?: Result.failure(
                PdaApiException.Ambiguous(
                    envelope.message ?: "The server accepted this but returned no document.",
                    attempt.startedAtEpochMs,
                ),
            )
    },
    onFailure = { Result.failure(it) },
)

/**
 * Folds a list response into a domain [Result], extracting the rows tolerantly
 * (see [PdaJson.rowsOf]) and skipping any single row that fails to parse rather
 * than failing the whole list.
 */
fun <D, R> Result<JsonElement>.mapRows(
    deserializer: DeserializationStrategy<D>,
    transform: (D) -> R,
): Result<List<R>> = fold(
    onSuccess = { root ->
        val rows = PdaJson.rowsOf(root).mapNotNull { element ->
            runCatching { PdaJson.instance.decodeFromJsonElement(deserializer, element) }
                .getOrNull()
                ?.let(transform)
        }
        Result.success(rows)
    },
    onFailure = { Result.failure(it) },
)
