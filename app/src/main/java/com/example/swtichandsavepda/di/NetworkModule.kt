package com.example.swtichandsavepda.di

import com.example.swtichandsavepda.BuildConfig
import com.example.swtichandsavepda.data.remote.PdaApiService
import com.example.swtichandsavepda.data.remote.PdaJson
import com.example.swtichandsavepda.data.remote.WriteAttemptEventListenerFactory
import com.example.swtichandsavepda.data.remote.interceptor.AuthInterceptor
import com.example.swtichandsavepda.data.remote.interceptor.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The Retrofit/OkHttp stack for the PDA portal. Everything is a singleton — one
 * connection pool, one client, one Retrofit instance for the whole app.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .eventListenerFactory(WriteAttemptEventListenerFactory)
            // The retry policy for a non-idempotent stock write belongs to the
            // app, not to the HTTP client: OkHttp's default can re-send a POST
            // over a fresh route when the portal may already have consumed the
            // first one. Reads compensate for this in safeApiCall.
            .retryOnConnectionFailure(false)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (BuildConfig.NETWORK_LOGGING) {
            builder.addInterceptor(bodySafeLoggingInterceptor())
        }

        return builder.build()
    }

    /**
     * BODY-level logging everywhere except the endpoints whose **request body is
     * a credential**.
     *
     * `redactHeader` cannot help there: the password sits in the JSON body, not
     * in a header, so a plain BODY logger prints it to logcat in full. This is
     * debug-only (`NETWORK_LOGGING` is false in release), but a debug build is
     * exactly where screen shares, bug reports and CI logs come from.
     */
    private fun bodySafeLoggingInterceptor(): Interceptor {
        val full = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
        }
        val headersOnly = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
        }

        return Interceptor { chain ->
            val path = chain.request().url.encodedPath
            val delegate = if (CREDENTIAL_PATHS.any { path.endsWith(it) }) headersOnly else full
            delegate.intercept(chain)
        }
    }

    /** Paths whose request body carries a secret. */
    private val CREDENTIAL_PATHS = listOf("/api/pda/login")

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.PDA_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(PdaJson.instance.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun providePdaApiService(retrofit: Retrofit): PdaApiService =
        retrofit.create(PdaApiService::class.java)
}
