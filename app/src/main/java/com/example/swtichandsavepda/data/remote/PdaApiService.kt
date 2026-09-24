package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.remote.dto.DocumentEnvelope
import com.example.swtichandsavepda.data.remote.dto.LoginRequest
import com.example.swtichandsavepda.data.remote.dto.LoginResponse
import com.example.swtichandsavepda.data.remote.dto.MeResponse
import com.example.swtichandsavepda.data.remote.dto.ProductPriceDto
import com.example.swtichandsavepda.data.remote.dto.ProductPriceRequest
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderRequest
import com.example.swtichandsavepda.data.remote.dto.PurchaseReturnDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseReturnRequest
import com.example.swtichandsavepda.data.remote.dto.ReceivePurchaseOrderRequest
import com.example.swtichandsavepda.data.remote.dto.SimpleResponse
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentDto
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

/**
 * The PDA portal contract (`https://retail-portal.sspos.co.uk`). Every path is
 * relative to the base URL's trailing slash.
 *
 * List endpoints return a raw [JsonElement] because the portal's pagination
 * envelope is not fixed in the spec; [PdaJson.rowsOf] extracts the rows
 * tolerantly in the repositories.
 */
interface PdaApiService {

    // ── Authentication ──────────────────────────────────────────────────────
    @POST("api/pda/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/pda/me")
    suspend fun me(): MeResponse

    @POST("api/pda/logout")
    suspend fun logout(): SimpleResponse

    // ── Reference lookups (pick lists) ──────────────────────────────────────
    @GET("api/pda/products")
    suspend fun searchProducts(
        @Query("search") search: String?,
        @Query("barcode") barcode: String?,
        @Query("per_page") perPage: Int?,
    ): JsonElement

    @GET("api/pda/suppliers")
    suspend fun searchSuppliers(
        @Query("search") search: String?,
        @Query("per_page") perPage: Int?,
    ): JsonElement

    @GET("api/pda/stock-locations")
    suspend fun searchStockLocations(@Query("search") search: String?): JsonElement

    /**
     * Multi-UOM scan path: a barcode resolves to the product **and the unit** it
     * maps to. Returns an array — one barcode can match more than one unit.
     */
    @GET("api/pda/resolve-barcode")
    suspend fun resolveBarcode(@Query("barcode") barcode: String): JsonElement

    /** The units a product can be bought/returned/adjusted in (unit selector). */
    @GET("api/pda/products/{id}/units")
    suspend fun productUnits(@Path("id") productId: Long): JsonElement

    /**
     * Sets the product's base retail price and queues it for the POS's next
     * product sync. Per-unit prices and cost are not touched.
     */
    @PUT("api/pda/products/{id}/price")
    suspend fun updateProductPrice(
        @Path("id") productId: Long,
        @Body body: ProductPriceRequest,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<ProductPriceDto>

    // ── Purchase orders ─────────────────────────────────────────────────────
    @POST("api/pda/purchase-orders")
    suspend fun createPurchaseOrder(
        @Body body: PurchaseOrderRequest,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<PurchaseOrderDto>

    @PUT("api/pda/purchase-orders/{id}")
    suspend fun updatePurchaseOrder(
        @Path("id") id: Long,
        @Body body: PurchaseOrderRequest,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<PurchaseOrderDto>

    @POST("api/pda/purchase-orders/{id}/receive")
    suspend fun receivePurchaseOrder(
        @Path("id") id: Long,
        @Body body: ReceivePurchaseOrderRequest,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<PurchaseOrderDto>

    @GET("api/pda/purchase-orders")
    suspend fun listPurchaseOrders(): JsonElement

    /** Receiving history for one PO — one row per GRN, newest first. */
    @GET("api/pda/purchase-orders/{id}/receipts")
    suspend fun purchaseOrderReceipts(@Path("id") id: Long): JsonElement

    /**
     * What can still be returned against a PO: received minus already returned,
     * per line. The portal is the authority on this, so the app never computes
     * it from two other calls.
     */
    @GET("api/pda/purchase-orders/{id}/returnable")
    suspend fun purchaseOrderReturnable(@Path("id") id: Long): JsonElement

    @POST("api/pda/purchase-orders/{id}/cancel")
    suspend fun cancelPurchaseOrder(
        @Path("id") id: Long,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<PurchaseOrderDto>

    // ── Purchase returns ────────────────────────────────────────────────────
    @POST("api/pda/purchase-returns")
    suspend fun createPurchaseReturn(
        @Body body: PurchaseReturnRequest,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<PurchaseReturnDto>

    @GET("api/pda/purchase-returns")
    suspend fun listPurchaseReturns(
        @Query("purchase_order_id") purchaseOrderId: Long? = null,
    ): JsonElement

    @PUT("api/pda/purchase-returns/{id}")
    suspend fun updatePurchaseReturn(
        @Path("id") id: Long,
        @Body body: PurchaseReturnRequest,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<PurchaseReturnDto>

    @POST("api/pda/purchase-returns/{id}/cancel")
    suspend fun cancelPurchaseReturn(
        @Path("id") id: Long,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<PurchaseReturnDto>

    // ── Stock adjustments ───────────────────────────────────────────────────
    @POST("api/pda/stock-adjustments")
    suspend fun createStockAdjustment(
        @Body body: StockAdjustmentRequest,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<StockAdjustmentDto>

    @GET("api/pda/stock-adjustments")
    suspend fun listStockAdjustments(): JsonElement

    @PUT("api/pda/stock-adjustments/{id}")
    suspend fun updateStockAdjustment(
        @Path("id") id: Long,
        @Body body: StockAdjustmentRequest,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<StockAdjustmentDto>

    @POST("api/pda/stock-adjustments/{id}/cancel")
    suspend fun cancelStockAdjustment(
        @Path("id") id: Long,
        @Tag attempt: WriteAttempt,
    ): DocumentEnvelope<StockAdjustmentDto>
}
