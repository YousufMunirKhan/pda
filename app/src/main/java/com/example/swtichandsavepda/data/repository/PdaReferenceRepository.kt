package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.BarcodeMatch
import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.StockLocationRef
import com.example.swtichandsavepda.data.model.SupplierRef
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.dto.BarcodeMatchDto
import com.example.swtichandsavepda.data.remote.dto.ProductRefDto
import com.example.swtichandsavepda.data.remote.dto.ProductUnitDto
import com.example.swtichandsavepda.data.remote.dto.StockLocationRefDto
import com.example.swtichandsavepda.data.remote.dto.SupplierRefDto
import com.example.swtichandsavepda.data.remote.mapRows
import com.example.swtichandsavepda.data.remote.safeApiCall
import com.example.swtichandsavepda.data.remote.toDomain
import javax.inject.Inject
import javax.inject.Singleton

/** Portal pick lists that back the product / supplier / location pickers. */
interface PdaReferenceRepository {

    /** Search by barcode / name / code. Blank [query] returns the first page. */
    suspend fun searchProducts(query: String): Result<List<ProductRef>>

    /** Exact barcode match — the one product, or null. Legacy scanner fallback. */
    suspend fun findProductByBarcode(barcode: String): Result<ProductRef?>

    /**
     * The Multi-UOM scan path: a barcode → the product *and* the unit it maps to.
     * May return several matches (one barcode registered against two units), or
     * none for a product the portal has no unit rows for.
     */
    suspend fun resolveBarcode(barcode: String): Result<List<BarcodeMatch>>

    /** The units a product can be transacted in, for the unit selector. */
    suspend fun productUnits(productId: Long): Result<List<ProductUnit>>

    suspend fun searchSuppliers(query: String): Result<List<SupplierRef>>

    suspend fun searchLocations(query: String): Result<List<StockLocationRef>>
}

@Singleton
class PdaReferenceRepositoryImpl @Inject constructor(
    private val api: PdaApiService,
) : PdaReferenceRepository {

    override suspend fun searchProducts(query: String): Result<List<ProductRef>> =
        safeApiCall { api.searchProducts(query.trim().ifBlank { null }, null, PER_PAGE) }
            .mapRows(ProductRefDto.serializer(), ProductRefDto::toDomain)

    override suspend fun findProductByBarcode(barcode: String): Result<ProductRef?> =
        safeApiCall { api.searchProducts(null, barcode.trim(), 1) }
            .mapRows(ProductRefDto.serializer(), ProductRefDto::toDomain)
            .map { it.firstOrNull() }

    override suspend fun resolveBarcode(barcode: String): Result<List<BarcodeMatch>> =
        safeApiCall { api.resolveBarcode(barcode.trim()) }
            .mapRows(BarcodeMatchDto.serializer(), BarcodeMatchDto::toDomain)

    override suspend fun productUnits(productId: Long): Result<List<ProductUnit>> =
        safeApiCall { api.productUnits(productId) }
            .mapRows(ProductUnitDto.serializer(), ProductUnitDto::toDomain)

    override suspend fun searchSuppliers(query: String): Result<List<SupplierRef>> =
        safeApiCall { api.searchSuppliers(query.trim().ifBlank { null }, PER_PAGE) }
            .mapRows(SupplierRefDto.serializer(), SupplierRefDto::toDomain)

    override suspend fun searchLocations(query: String): Result<List<StockLocationRef>> =
        safeApiCall { api.searchStockLocations(query.trim().ifBlank { null }) }
            .mapRows(StockLocationRefDto.serializer(), StockLocationRefDto::toDomain)

    private companion object {
        const val PER_PAGE = 25
    }
}
