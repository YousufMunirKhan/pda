package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.StockAdjustmentDoc
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.WriteAttempt
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentDto
import com.example.swtichandsavepda.data.remote.mapDocument
import com.example.swtichandsavepda.data.remote.mapRows
import com.example.swtichandsavepda.data.remote.safeApiCall
import com.example.swtichandsavepda.data.remote.safeWriteCall
import com.example.swtichandsavepda.data.remote.toDomain
import com.example.swtichandsavepda.data.remote.toRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Stock adjustments on the PDA portal — one call, all five modes. */
interface PdaStockAdjustmentRepository {

    suspend fun create(adjustment: NewStockAdjustment): Result<StockAdjustmentDoc>

    suspend fun edit(id: Long, adjustment: NewStockAdjustment): Result<StockAdjustmentDoc>

    suspend fun list(): Result<List<StockAdjustmentDoc>>

    suspend fun cancel(id: Long): Result<StockAdjustmentDoc>
}

@Singleton
class PdaStockAdjustmentRepositoryImpl @Inject constructor(
    private val api: PdaApiService,
) : PdaStockAdjustmentRepository {

    /**
     * Creating an adjustment moves stock and is not idempotent, so an ambiguous
     * outcome is resolved by asking the portal what it holds — never by
     * re-sending. See [resolveIfAmbiguous].
     */
    override suspend fun create(adjustment: NewStockAdjustment): Result<StockAdjustmentDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) { api.createStockAdjustment(adjustment.toRequest(), attempt) }
            .mapDocument(attempt, StockAdjustmentDto::toDomain)
            .resolveIfAmbiguous(
                list = ::list,
                matches = adjustment.matcher(attempt.startedAtEpochMs),
            )
    }

    override suspend fun edit(
        id: Long,
        adjustment: NewStockAdjustment,
    ): Result<StockAdjustmentDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) {
            api.updateStockAdjustment(id, adjustment.toRequest(), attempt)
        }.mapDocument(attempt, StockAdjustmentDto::toDomain)
    }

    override suspend fun list(): Result<List<StockAdjustmentDoc>> =
        safeApiCall { api.listStockAdjustments() }
            .mapRows(StockAdjustmentDto.serializer(), StockAdjustmentDto::toDomain)

    override suspend fun cancel(id: Long): Result<StockAdjustmentDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) { api.cancelStockAdjustment(id, attempt) }
            .mapDocument(attempt, StockAdjustmentDto::toDomain)
    }
}

/**
 * Identifies the draft this attempt would have produced.
 *
 * The portal echoes no client reference, so this matches on the fields that
 * define the movement plus a created-at window: without the window an operator
 * legitimately booking the same quantity twice in a day would have the second
 * attempt "recognised" as the first, silently dropping it.
 *
 * NEEDS VERIFICATION: if the portal can be made to accept and echo a
 * `client_reference`, replace all of this with an exact lookup.
 */
internal fun NewStockAdjustment.matcher(
    attemptStartedAtEpochMs: Long,
): (StockAdjustmentDoc) -> Boolean = { doc ->
    val expectedBase = UomMath.baseQuantity(quantity, unit?.conversionToBase ?: 1.0)
    doc.productId == productId &&
        doc.adjustmentType == mode.adjustmentType &&
        doc.direction == mode.direction &&
        doc.quantity.isCloseTo(expectedBase) &&
        // A document created before we started cannot be ours.
        (doc.createdAtEpochMs?.let { it >= attemptStartedAtEpochMs - CLOCK_SKEW_MS } ?: false)
}
