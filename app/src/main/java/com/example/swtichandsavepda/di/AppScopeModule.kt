package com.example.swtichandsavepda.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the application-lifetime scope that stock writes run on.
 *
 * A create/receive/cancel must **outlive the screen that started it**. On
 * `viewModelScope` a back-press, a forced logout, or any `popUpTo` cancels the
 * coroutine — but not the request, which may already have reached the portal.
 * The app would then never learn whether the document exists, and the operator
 * would re-key it.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class WriteScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    /**
     * [SupervisorJob] so one failed write cannot cancel another's reconciliation,
     * and [Dispatchers.Default] because the work is a network call plus a small
     * amount of JSON — never the main thread.
     */
    @Provides
    @Singleton
    @WriteScope
    fun provideWriteScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
