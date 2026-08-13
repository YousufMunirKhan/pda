package com.example.swtichandsavepda.data.local

import android.content.Context
import com.example.swtichandsavepda.data.remote.PdaJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What happened to one attempt at creating a stock document. */
enum class AttemptState {
    /** Written before the request goes out. Anything still SENDING at startup died with the process. */
    SENDING,

    /** Confirmed on the portal — either by its response or by reconciliation. */
    CREATED,

    /** The portal answered and does not hold it. Safe to re-key. */
    NOT_CREATED,

    /** Sent, outcome never established. Needs the operator's eyes. */
    UNKNOWN,
}

/**
 * One row per write attempt.
 *
 * [summary] is what the operator would recognise ("Book in 5 Box · Coke 500ml"),
 * because the point of this record is to be shown to a human deciding whether to
 * re-key something.
 */
@Serializable
data class SubmissionAttempt(
    val id: String,
    val documentType: String,
    val summary: String,
    val startedAtEpochMs: Long,
    val state: AttemptState,
    val portalDocumentId: Long? = null,
)

/**
 * A durable record of in-flight stock writes, so a write that was sent is never
 * silently forgotten.
 *
 * Without this, killing the app mid-submit (Android reclaiming memory, a crash,
 * the PDA going in a pocket) leaves no trace that a POST was ever attempted: the
 * ViewModel is rebuilt empty and the operator re-keys a document the portal may
 * already hold.
 */
interface SubmissionJournal {

    /** Records an attempt as [AttemptState.SENDING]. Call **before** the request. */
    suspend fun begin(id: String, documentType: String, summary: String, startedAtEpochMs: Long)

    /** Records the outcome of an attempt started with [begin]. */
    suspend fun resolve(id: String, state: AttemptState, portalDocumentId: Long? = null)

    /** Drops a row once the operator has acknowledged it. */
    suspend fun forget(id: String)

    /**
     * Call once at startup. Any row still [AttemptState.SENDING] belongs to a
     * process that died mid-write, so its outcome can no longer be observed from
     * here — it becomes [AttemptState.UNKNOWN] and is surfaced to the operator.
     *
     * Deliberately **not** auto-reconciled: the journal holds a summary, not the
     * full payload, and matching a stock movement on partial data risks
     * announcing "already created" about somebody else's draft. Telling the
     * operator to check is the honest option, and it is what a payment app does
     * with an unresolved transaction it cannot enquire on.
     */
    suspend fun markOrphansUnknown()

    /** Attempts the operator still needs to know about: sent, outcome unproven. */
    suspend fun unresolved(): List<SubmissionAttempt>
}

/**
 * The on-disk journal.
 *
 * Backed by a single small JSON file rather than a database — this holds a
 * handful of rows at most, and Room would be a dependency and a migration story
 * for no benefit at that size. Writes are serialised through a [Mutex] and go
 * via a temp file, so a kill mid-write cannot leave a truncated journal.
 */
@Singleton
class SubmissionJournalImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SubmissionJournal {

    private val mutex = Mutex()
    private val file: File by lazy { File(context.filesDir, FILE_NAME) }

    override suspend fun begin(
        id: String,
        documentType: String,
        summary: String,
        startedAtEpochMs: Long,
    ) {
        update { attempts ->
            attempts + SubmissionAttempt(
                id = id,
                documentType = documentType,
                summary = summary,
                startedAtEpochMs = startedAtEpochMs,
                state = AttemptState.SENDING,
            )
        }
    }

    override suspend fun resolve(id: String, state: AttemptState, portalDocumentId: Long?) {
        update { attempts ->
            attempts.map { attempt ->
                if (attempt.id == id) {
                    attempt.copy(state = state, portalDocumentId = portalDocumentId)
                } else {
                    attempt
                }
            }
        }
    }

    override suspend fun forget(id: String) {
        update { attempts -> attempts.filterNot { it.id == id } }
    }

    override suspend fun markOrphansUnknown() {
        update { attempts ->
            attempts.map {
                if (it.state == AttemptState.SENDING) it.copy(state = AttemptState.UNKNOWN) else it
            }
        }
    }

    override suspend fun unresolved(): List<SubmissionAttempt> =
        read().filter { it.state == AttemptState.SENDING || it.state == AttemptState.UNKNOWN }

    private suspend fun read(): List<SubmissionAttempt> = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked() }
    }

    private fun readLocked(): List<SubmissionAttempt> {
        if (!file.exists()) return emptyList()
        // A corrupt journal must not brick the app on launch — an unreadable
        // record is no worse than the no-record status quo.
        return runCatching {
            PdaJson.instance.decodeFromString<List<SubmissionAttempt>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun update(transform: (List<SubmissionAttempt>) -> List<SubmissionAttempt>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = transform(readLocked()).takeLast(MAX_ROWS)
                val temp = File(file.parentFile, "$FILE_NAME.tmp")
                runCatching {
                    temp.writeText(PdaJson.instance.encodeToString(next))
                    // Atomic swap: a kill mid-write leaves the previous journal intact.
                    if (!temp.renameTo(file)) {
                        file.writeText(temp.readText())
                        temp.delete()
                    }
                }
            }
        }
    }

    private companion object {
        const val FILE_NAME = "submission_journal.json"

        /** A runaway journal helps nobody; the newest rows are the actionable ones. */
        const val MAX_ROWS = 50
    }
}
