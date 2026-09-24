package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.ProductPriceChange
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.WriteAttempt
import com.example.swtichandsavepda.data.remote.dto.ProductPriceDto
import com.example.swtichandsavepda.data.remote.dto.ProductPriceRequest
import com.example.swtichandsavepda.data.remote.mapDocument
import com.example.swtichandsavepda.data.remote.safeWriteCall
import javax.inject.Inject
import javax.inject.Singleton

/** Retail price changes made from the PDA (`PUT /api/pda/products/{id}/price`). */
interface PdaProductPriceRepository {

    /**
     * Sets the product's **base** retail price. Per-unit (Multi-UOM) prices and
     * cost are left alone by the portal.
     *
     * Setting an absolute price is idempotent, so unlike a stock write an
     * ambiguous outcome is safe to resolve by simply saving again.
     */
    suspend fun updateRetailPrice(
        productId: Long,
        retail: Double,
        clientReference: String? = null,
    ): Result<ProductPriceChange>
}

@Singleton
class PdaProductPriceRepositoryImpl @Inject constructor(
    private val api: PdaApiService,
) : PdaProductPriceRepository {

    override suspend fun updateRetailPrice(
        productId: Long,
        retail: Double,
        clientReference: String?,
    ): Result<ProductPriceChange> {
        val attempt = WriteAttempt()
        return safeWriteCall(attempt) {
            api.updateProductPrice(productId, ProductPriceRequest(retail, clientReference), attempt)
        }.mapDocument(attempt) { dto -> dto.toDomain(fallbackRetail = retail) }
    }
}

private fun ProductPriceDto.toDomain(fallbackRetail: Double) = ProductPriceChange(
    productId = id,
    productName = productName,
    barcode = barcode,
    previousRetail = previousRetail,
    retail = retail ?: fallbackRetail,
)
