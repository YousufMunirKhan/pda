package com.example.swtichandsavepda.di

import com.example.swtichandsavepda.data.repository.PdaAuthRepository
import com.example.swtichandsavepda.data.repository.PdaAuthRepositoryImpl
import com.example.swtichandsavepda.data.repository.PdaPurchaseOrderRepository
import com.example.swtichandsavepda.data.repository.PdaPurchaseOrderRepositoryImpl
import com.example.swtichandsavepda.data.repository.PdaPurchaseReturnRepository
import com.example.swtichandsavepda.data.repository.PdaPurchaseReturnRepositoryImpl
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
import com.example.swtichandsavepda.data.repository.PdaReferenceRepositoryImpl
import com.example.swtichandsavepda.data.repository.PdaStockAdjustmentRepository
import com.example.swtichandsavepda.data.repository.PdaStockAdjustmentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the live PDA-portal repositories to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PdaRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPdaAuthRepository(impl: PdaAuthRepositoryImpl): PdaAuthRepository

    @Binds
    @Singleton
    abstract fun bindPdaPurchaseOrderRepository(
        impl: PdaPurchaseOrderRepositoryImpl,
    ): PdaPurchaseOrderRepository

    @Binds
    @Singleton
    abstract fun bindPdaPurchaseReturnRepository(
        impl: PdaPurchaseReturnRepositoryImpl,
    ): PdaPurchaseReturnRepository

    @Binds
    @Singleton
    abstract fun bindPdaStockAdjustmentRepository(
        impl: PdaStockAdjustmentRepositoryImpl,
    ): PdaStockAdjustmentRepository

    @Binds
    @Singleton
    abstract fun bindPdaReferenceRepository(
        impl: PdaReferenceRepositoryImpl,
    ): PdaReferenceRepository
}
