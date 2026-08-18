package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.NewPurchaseReturnLine
import com.example.swtichandsavepda.data.model.PurchaseReturnDoc
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.WriteAttempt
import com.example.swtichandsavepda.data.remote.dto.PurchaseReturnDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseReturnRequest
import com.example.swtichandsavepda.data.remote.mapDocument
import com.example.swtichandsavepda.data.remote.mapRows
import com.example.swtichandsavepda.data.remote.safeApiCall
import com.example.swtichandsavepda.data.remote.safeWriteCall
import com.example.swtichandsavepda.data.remote.toDomain
import com.example.swtichandsavepda.data.remote.toRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Purchase returns on the PDA portal — create, list, edit, cancel. */
interface PdaPurchaseReturnRepository {

    /**
     * [purchaseOrderId] links the return to the PO the goods arrived on; omit it
     * for a plain supplier-level return. [clientReference] is the idempotency key.
     */
    suspend fun create(
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
        purchaseOrderId: Long? = null,
        clientReference: String? = null,
    ): Result<PurchaseReturnDoc>

    suspend fun edit(
        id: Long,
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
    ): Result<PurchaseReturnDoc>

    suspend fun list(): Result<List<PurchaseReturnDoc>>

    suspend fun cancel(id: Long): Result<PurchaseReturnDoc>
}

@Singleton
class PdaPurchaseReturnRepositoryImpl @Inject constructor(
    private val api: PdaApiService,
) : PdaPurchaseReturnRepository {

    /**
     * An ambiguous create reconciles on [clientReference], which the portal
     * guarantees unique per shop.
     *
     * It deliberately does **not** match on `reference_no`. The portal team
     * confirmed that field is free text and not unique, so matching on it could
     * recognise somebody else's return as this one and silently drop a real
     * stock movement.
     */
    override suspend fun create(
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
        purchaseOrderId: Long?,
        clientReference: String?,
    ): Result<PurchaseReturnDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) {
            api.createPurchaseReturn(
                buildRequest(
                    supplierId = supplierId,
                    referenceNo = referenceNo,
                    returnReason = returnReason,
                    lines = lines,
                    purchaseOrderId = purchaseOrderId,
                    clientReference = clientReference,
                ),
                attempt,
            )
        }.mapDocument(attempt, PurchaseReturnDto::toDomain)
            .let { result ->
                // With no key there is nothing to match on, and a matcher that
                // never matches would resolve to "not created — safe to try
                // again", which is the one conclusion we must not reach from
                // ignorance. Leave it ambiguous instead.
                if (clientReference == null) {
                    result
                } else {
                    result.resolveIfAmbiguous(
                        list = ::list,
                        matches = { doc -> doc.clientReference == clientReference },
                    )
                }
            }
    }

    override suspend fun edit(
        id: Long,
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
    ): Result<PurchaseReturnDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) {
            api.updatePurchaseReturn(
                id,
                buildRequest(supplierId, referenceNo, returnReason, lines),
                attempt,
            )
        }.mapDocument(attempt, PurchaseReturnDto::toDomain)
    }

    override suspend fun list(): Result<List<PurchaseReturnDoc>> =
        safeApiCall { api.listPurchaseReturns() }
            .mapRows(PurchaseReturnDto.serializer(), PurchaseReturnDto::toDomain)

    override suspend fun cancel(id: Long): Result<PurchaseReturnDoc> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) { api.cancelPurchaseReturn(id, attempt) }
            .mapDocument(attempt, PurchaseReturnDto::toDomain)
    }

    private fun buildRequest(
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
        purchaseOrderId: Long? = null,
        clientReference: String? = null,
    ) = PurchaseReturnRequest(
        clientReference = clientReference,
        supplierId = supplierId,
        purchaseOrderId = purchaseOrderId,
        referenceNo = referenceNo,
        returnReason = returnReason,
        items = lines.map { it.toRequest() },
    )
}
