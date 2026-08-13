package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.NewPurchaseOrderLine
import com.example.swtichandsavepda.data.model.PurchaseOrderDoc
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderRequest
import com.example.swtichandsavepda.data.remote.dto.ReceiveItemRequest
import com.example.swtichandsavepda.data.remote.dto.ReceivePurchaseOrderRequest
import com.example.swtichandsavepda.data.remote.mapDocument
import com.example.swtichandsavepda.data.remote.mapRows
import com.example.swtichandsavepda.data.remote.safeApiCall
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
    ): Result<PurchaseOrderDoc> =
        safeApiCall { api.createPurchaseOrder(buildRequest(supplierId, expectedDeliveryDate, lines)) }
            .mapDocument(PurchaseOrderDto::toDomain)

    override suspend fun edit(
        id: Long,
        supplierId: Long,
        expectedDeliveryDate: String,
        lines: List<NewPurchaseOrderLine>,
    ): Result<PurchaseOrderDoc> =
        safeApiCall {
            api.updatePurchaseOrder(id, buildRequest(supplierId, expectedDeliveryDate, lines))
        }.mapDocument(PurchaseOrderDto::toDomain)

    override suspend fun receive(
        id: Long,
        receivedByProduct: Map<Long, Double>,
    ): Result<PurchaseOrderDoc> {
        val body = ReceivePurchaseOrderRequest(
            items = receivedByProduct
                .takeIf { it.isNotEmpty() }
                ?.map { (productId, quantity) -> ReceiveItemRequest(productId, quantity) },
        )
        return safeApiCall { api.receivePurchaseOrder(id, body) }
            .mapDocument(PurchaseOrderDto::toDomain)
    }

    override suspend fun list(): Result<List<PurchaseOrderDoc>> =
        safeApiCall { api.listPurchaseOrders() }
            .mapRows(PurchaseOrderDto.serializer(), PurchaseOrderDto::toDomain)

    override suspend fun cancel(id: Long): Result<PurchaseOrderDoc> =
        safeApiCall { api.cancelPurchaseOrder(id) }.mapDocument(PurchaseOrderDto::toDomain)

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
