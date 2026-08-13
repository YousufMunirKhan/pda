package com.example.swtichandsavepda.data.repository

import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.StockAdjustmentDoc
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentDto
import com.example.swtichandsavepda.data.remote.mapDocument
import com.example.swtichandsavepda.data.remote.mapRows
import com.example.swtichandsavepda.data.remote.safeApiCall
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

    override suspend fun create(adjustment: NewStockAdjustment): Result<StockAdjustmentDoc> =
        safeApiCall { api.createStockAdjustment(adjustment.toRequest()) }
            .mapDocument(StockAdjustmentDto::toDomain)

    override suspend fun edit(
        id: Long,
        adjustment: NewStockAdjustment,
    ): Result<StockAdjustmentDoc> =
        safeApiCall { api.updateStockAdjustment(id, adjustment.toRequest()) }
            .mapDocument(StockAdjustmentDto::toDomain)

    override suspend fun list(): Result<List<StockAdjustmentDoc>> =
        safeApiCall { api.listStockAdjustments() }
            .mapRows(StockAdjustmentDto.serializer(), StockAdjustmentDto::toDomain)

    override suspend fun cancel(id: Long): Result<StockAdjustmentDoc> =
        safeApiCall { api.cancelStockAdjustment(id) }.mapDocument(StockAdjustmentDto::toDomain)

}
