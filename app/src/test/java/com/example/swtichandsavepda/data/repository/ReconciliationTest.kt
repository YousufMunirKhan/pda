package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.AdjustmentMode
import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.StockAdjustmentDoc
import com.example.swtichandsavepda.data.remote.PdaApiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An ambiguous write is resolved by asking the portal what it holds — never by
 * re-sending. These pin the three outcomes and, crucially, that "we couldn't
 * check" never collapses into "it isn't there".
 */
class ReconciliationTest {

    private val startedAt = 1_000_000L

    private val box = ProductUnit(
        productUnitId = 3,
        selectedUnitId = 8,
        code = "BOX",
        name = "Box",
        barcode = null,
        conversionToBase = 12.0,
        retailPrice = null,
        purchaseCost = 24.0,
        isBaseUnit = false,
        allowDecimal = false,
        decimalPlaces = 0,
    )

    private val adjustment = NewStockAdjustment(
        productId = 55,
        mode = AdjustmentMode.INCREASE,
        quantity = 5.0,
        unit = box,
        unitCost = 24.0,
    )

    private fun doc(
        id: Long,
        quantity: Double = 60.0,
        productId: Long = 55,
        createdAt: Long? = startedAt + 500,
    ) = StockAdjustmentDoc(
        id = id,
        productId = productId,
        productName = null,
        adjustmentType = "Stock Increase",
        direction = "IN",
        quantity = quantity,
        unitCost = 2.0,
        reason = null,
        portalState = PortalState.PENDING,
        rejectReason = null,
        createdAtEpochMs = createdAt,
    )

    private val ambiguous: Result<StockAdjustmentDoc> =
        Result.failure(PdaApiException.Ambiguous("dropped", startedAt))

    @Test
    fun `a matching draft on the portal resolves the write as created`() = runTest {
        // The entered 5 Box became a base quantity of 60 — the matcher must
        // compare in base units, as the portal stores them.
        val resolved = ambiguous.resolveIfAmbiguous(
            list = { Result.success(listOf(doc(id = 9))) },
            matches = adjustment.matcher(startedAt),
        )

        assertEquals(9L, resolved.getOrNull()?.id)
    }

    @Test
    fun `an empty portal list means safe to retry`() = runTest {
        val resolved = ambiguous.resolveIfAmbiguous(
            list = { Result.success(emptyList()) },
            matches = adjustment.matcher(startedAt),
        )

        val error = resolved.exceptionOrNull()
        assertTrue(error is PdaApiException.Network)
        assertTrue(error!!.message!!.contains("safe to try again"))
    }

    @Test
    fun `an unreachable portal leaves the write unresolved, never retryable`() = runTest {
        // The critical case: we could not check. Collapsing this into
        // "not created" is exactly how a duplicate is produced.
        val resolved = ambiguous.resolveIfAmbiguous(
            list = { Result.failure(PdaApiException.Network("offline")) },
            matches = adjustment.matcher(startedAt),
        )

        assertTrue(resolved.exceptionOrNull() is PdaApiException.Ambiguous)
    }

    @Test
    fun `an identical draft created before this attempt is not ours`() = runTest {
        // The operator legitimately booked the same quantity earlier today.
        // Without the created-at window we would call this attempt "already
        // created" and silently drop the second movement.
        val resolved = ambiguous.resolveIfAmbiguous(
            list = { Result.success(listOf(doc(id = 4, createdAt = startedAt - 10 * 60_000))) },
            matches = adjustment.matcher(startedAt),
        )

        assertTrue(resolved.exceptionOrNull() is PdaApiException.Network)
    }

    @Test
    fun `a draft created just inside the clock-skew window still counts as ours`() = runTest {
        // The PDA's clock can run ahead of the portal's, so a document stamped a
        // little before we started is still plausibly this attempt.
        val resolved = ambiguous.resolveIfAmbiguous(
            list = { Result.success(listOf(doc(id = 4, createdAt = startedAt - CLOCK_SKEW_MS + 1))) },
            matches = adjustment.matcher(startedAt),
        )

        assertEquals(4L, resolved.getOrNull()?.id)
    }

    @Test
    fun `a document with no created_at is never matched`() = runTest {
        // Guessing on a missing timestamp risks claiming somebody else's draft.
        val resolved = ambiguous.resolveIfAmbiguous(
            list = { Result.success(listOf(doc(id = 4, createdAt = null))) },
            matches = adjustment.matcher(startedAt),
        )

        assertTrue(resolved.exceptionOrNull() is PdaApiException.Network)
    }

    @Test
    fun `a different product is not a match`() = runTest {
        val resolved = ambiguous.resolveIfAmbiguous(
            list = { Result.success(listOf(doc(id = 9, productId = 999))) },
            matches = adjustment.matcher(startedAt),
        )

        assertTrue(resolved.exceptionOrNull() is PdaApiException.Network)
    }

    @Test
    fun `a non-ambiguous failure passes straight through untouched`() = runTest {
        val validation: Result<StockAdjustmentDoc> =
            Result.failure(PdaApiException.Validation("Check the form"))

        val resolved = validation.resolveIfAmbiguous(
            list = { error("the portal must not be consulted for a 422") },
            matches = { true },
        )

        assertTrue(resolved.exceptionOrNull() is PdaApiException.Validation)
    }

    @Test
    fun `a success passes straight through untouched`() = runTest {
        val ok: Result<StockAdjustmentDoc> = Result.success(doc(id = 3))

        val resolved = ok.resolveIfAmbiguous(
            list = { error("the portal must not be consulted on success") },
            matches = { true },
        )

        assertEquals(3L, resolved.getOrNull()?.id)
    }
}
