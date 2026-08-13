package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.NewPurchaseReturnLine
import com.example.swtichandsavepda.data.model.PurchaseReturnDoc
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.dto.PurchaseReturnDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseReturnRequest
import com.example.swtichandsavepda.data.remote.mapDocument
import com.example.swtichandsavepda.data.remote.mapRows
import com.example.swtichandsavepda.data.remote.safeApiCall
import com.example.swtichandsavepda.data.remote.toDomain
import com.example.swtichandsavepda.data.remote.toRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Purchase returns on the PDA portal — create, list, edit, cancel. */
interface PdaPurchaseReturnRepository {

    suspend fun create(
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
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

    override suspend fun create(
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
    ): Result<PurchaseReturnDoc> =
        safeApiCall {
            api.createPurchaseReturn(buildRequest(supplierId, referenceNo, returnReason, lines))
        }.mapDocument(PurchaseReturnDto::toDomain)

    override suspend fun edit(
        id: Long,
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
    ): Result<PurchaseReturnDoc> =
        safeApiCall {
            api.updatePurchaseReturn(id, buildRequest(supplierId, referenceNo, returnReason, lines))
        }.mapDocument(PurchaseReturnDto::toDomain)

    override suspend fun list(): Result<List<PurchaseReturnDoc>> =
        safeApiCall { api.listPurchaseReturns() }
            .mapRows(PurchaseReturnDto.serializer(), PurchaseReturnDto::toDomain)

    override suspend fun cancel(id: Long): Result<PurchaseReturnDoc> =
        safeApiCall { api.cancelPurchaseReturn(id) }.mapDocument(PurchaseReturnDto::toDomain)

    private fun buildRequest(
        supplierId: Long,
        referenceNo: String,
        returnReason: String,
        lines: List<NewPurchaseReturnLine>,
    ) = PurchaseReturnRequest(
        supplierId = supplierId,
        referenceNo = referenceNo,
        returnReason = returnReason,
        items = lines.map { it.toRequest() },
    )
}
