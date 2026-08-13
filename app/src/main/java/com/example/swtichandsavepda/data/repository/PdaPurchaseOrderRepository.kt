package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.NewPurchaseOrderLine
import com.example.swtichandsavepda.data.model.PurchaseOrderDoc
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.WriteAttempt
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderRequest
import com.example.swtichandsavepda.data.remote.dto.ReceiveItemRequest
import com.example.swtichandsavepda.data.remote.dto.ReceivePurchaseOrderRequest
import com.example.swtichandsavepda.data.remote.mapDocument
import com.example.swtichandsavepda.data.remote.mapRows
import com.example.swtichandsavepda.data.remote.safeApiCall
import com.example.swtichandsavepda.data.remote.safeWriteCall
import com.example.swtichandsavepda.data.remote.toDomain
import com.example.swtichandsavepda.data.remote.toRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Purchase orders on the PDA portal — create, edit, receive, list, cancel. */
interface PdaPurchaseOrderRepository {

    suspend fun create(
        supplierId: Long,
        expectedDeliveryDate: String,
        lines: List<NewPurchaseOrderLine>,
    ): Result<PurchaseOrderDoc>

    suspend fun edit(
        id: Long,
        supplierId: Long,
        expectedDeliveryDate: String,
        lines: List<NewPurchaseOrderLine>,
    ): Result<PurchaseOrderDoc>

    /** Receive everything ([receivedByProduct] empty) or the named quantities. */
    suspend fun receive(
        id: Long,
        receivedByProduct: Map<Long, Double> = emptyMap(),
    ): Result<PurchaseOrderDoc>

    suspend fun list(): Result<List<PurchaseOrderDoc>>

    suspend fun cancel(id: Long): Result<PurchaseOrderDoc>
}

@Singleton
class PdaPurchaseOrderRepositoryImpl @Inject constructor(
    private val api: PdaApiService,
) : PdaPurchaseOrderRepository {

    override suspend fun create(
        supplierId: Long,
        expectedDeliveryDate: String,
        lines: List<NewPurchaseOrderLine>,
    ): Result<PurchaseOrderDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) {
            api.createPurchaseOrder(
                buildRequest(supplierId, expectedDeliveryDate, lines),
                attempt,
            )
        }.mapDocument(attempt, PurchaseOrderDto::toDomain)
            .resolveIfAmbiguous(
                list = ::list,
                matches = poMatcher(supplierId, expectedDeliveryDate, lines, attempt.startedAtEpochMs),
            )
    }

    override suspend fun edit(
        id: Long,
        supplierId: Long,
        expectedDeliveryDate: String,
        lines: List<NewPurchaseOrderLine>,
    ): Result<PurchaseOrderDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) {
            api.updatePurchaseOrder(
                id,
                buildRequest(supplierId, expectedDeliveryDate, lines),
                attempt,
            )
        }.mapDocument(attempt, PurchaseOrderDto::toDomain)
    }

    /**
     * NEEDS VERIFICATION: whether a second receive on an already-received PO is
     * a no-op or double-receives. Until that is answered this is treated as
     * non-idempotent — an ambiguous receive is reported as such rather than
     * retried, but it cannot be auto-reconciled because a received PO looks the
     * same however many times it was received.
     */
    override suspend fun receive(
        id: Long,
        receivedByProduct: Map<Long, Double>,
    ): Result<PurchaseOrderDoc> {
        val body = ReceivePurchaseOrderRequest(
            items = receivedByProduct
                .takeIf { it.isNotEmpty() }
                ?.map { (productId, quantity) -> ReceiveItemRequest(productId, quantity) },
        )
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) { api.receivePurchaseOrder(id, body, attempt) }
            .mapDocument(attempt, PurchaseOrderDto::toDomain)
    }

    override suspend fun list(): Result<List<PurchaseOrderDoc>> =
        safeApiCall { api.listPurchaseOrders() }
            .mapRows(PurchaseOrderDto.serializer(), PurchaseOrderDto::toDomain)

    override suspend fun cancel(id: Long): Result<PurchaseOrderDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) { api.cancelPurchaseOrder(id, attempt) }
            .mapDocument(attempt, PurchaseOrderDto::toDomain)
    }

    private fun buildRequest(
        supplierId: Long,
        expectedDeliveryDate: String,
        lines: List<NewPurchaseOrderLine>,
    ) = PurchaseOrderRequest(
        supplierId = supplierId,
        expectedDeliveryDate = expectedDeliveryDate,
        items = lines.map { it.toRequest() },
    )
}

/**
 * Identifies the PO this attempt would have produced: same supplier, same
 * delivery date, same line count, and created no earlier than the attempt.
 *
 * NEEDS VERIFICATION: a `client_reference` echoed by the portal would replace
 * this heuristic with an exact lookup.
 */
internal fun poMatcher(
    supplierId: Long,
    expectedDeliveryDate: String,
    lines: List<NewPurchaseOrderLine>,
    attemptStartedAtEpochMs: Long,
): (PurchaseOrderDoc) -> Boolean = { doc ->
    doc.supplierId == supplierId &&
        doc.expectedDeliveryDate?.take(EXPECTED_DATE_LENGTH) == expectedDeliveryDate &&
        doc.lines.size == lines.size &&
        (doc.createdAtEpochMs?.let { it >= attemptStartedAtEpochMs - CLOCK_SKEW_MS } ?: false)
}

/** The portal echoes the delivery date as a full timestamp; compare the date part. */
private const val EXPECTED_DATE_LENGTH = 10
