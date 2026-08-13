package com.example.swtichandsavepda.presentation

import java.util.UUID

/**
 * How a submission ended, as three states rather than the usual success/error
 * pair.
 *
 * [Unresolved] is the one that matters. A stock write that was sent but whose
 * answer never came back is neither a success nor a failure, and showing it as a
 * failure — with the form still filled in and Submit still live — is what turns
 * one goods-in into two. It is rendered without a retry affordance.
 */
sealed interface SubmitOutcome {

    val message: String

    data class Created(override val message: String) : SubmitOutcome

    /** The write definitely did not happen. Safe to try again. */
    data class Failed(override val message: String) : SubmitOutcome

    /** Sent; outcome unknown. Never offer a retry from here. */
    data class Unresolved(override val message: String) : SubmitOutcome
}

/** Correlates a journal row with the write it describes. */
fun newAttemptId(): String = UUID.randomUUID().toString()
