package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.remote.dto.DocumentEnvelope
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentDto
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentRequest
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The central safety property of the write path: **a failure that arrives after
 * the request body was sent is ambiguous, not failed.**
 *
 * These run against a real OkHttp/Retrofit stack over a loopback socket, because
 * the distinction being tested is a property of the socket, not of our code —
 * mocking the API would assume away the thing under test.
 */
class WriteFailureClassificationTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PdaApiService

    private val body = StockAdjustmentRequest(
        productId = 55,
        adjustmentType = "Stock Increase",
        direction = "IN",
        quantity = 60.0,
        unitCost = 2.0,
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .eventListenerFactory(WriteAttemptEventListenerFactory)
            .retryOnConnectionFailure(false)
            .readTimeout(1, TimeUnit.SECONDS)
            .connectTimeout(1, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(
                PdaJson.instance.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(PdaApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun create(attempt: WriteAttempt) =
        safeWriteCall(attempt) { api.createStockAdjustment(body, attempt) }

    @Test
    fun `a read timeout after the body was sent is ambiguous, not a network failure`() = runTest {
        // The portal received the POST and never answered — the classic case in
        // which the draft exists and only the response was lost.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val attempt = WriteAttempt()
        val result = create(attempt)

        assertTrue(attempt.requestFullySent)
        assertTrue(
            "expected Ambiguous, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is PdaApiException.Ambiguous,
        )
        // Exactly one request reached the server: nothing retried it.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 500 after the body was sent is ambiguous`() = runTest {
        // Laravel may have committed the row before the handler blew up.
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))

        val result = create(WriteAttempt())

        assertTrue(result.exceptionOrNull() is PdaApiException.Ambiguous)
    }

    @Test
    fun `a 422 is a clean validation failure, not ambiguous`() = runTest {
        // 4xx is a pre-write rejection: the portal decided not to create anything.
        server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"success":false,"message":"Quantity must be > 0"}"""),
        )

        val result = create(WriteAttempt())

        assertTrue(result.exceptionOrNull() is PdaApiException.Validation)
    }

    @Test
    fun `a connection refused before sending is a clean failure that invites retry`() = runTest {
        server.shutdown() // nothing listening: the body never leaves the device

        val attempt = WriteAttempt()
        val result = create(attempt)

        assertTrue(!attempt.requestFullySent)
        val error = result.exceptionOrNull()
        assertTrue("expected Network, got $error", error is PdaApiException.Network)
        // This is the ONLY post-failure message allowed to suggest trying again.
        assertTrue(error!!.message!!.contains("safe to try again"))
    }

    @Test
    fun `a 2xx we cannot read is ambiguous, never a failure`() = runTest {
        // The portal accepted it; we just could not parse the answer. Reporting
        // this as failed would invite a retry against a document that exists.
        server.enqueue(MockResponse().setResponseCode(201).setBody("not json at all"))

        val result = create(WriteAttempt())

        assertTrue(result.exceptionOrNull() is PdaApiException.Ambiguous)
    }

    @Test
    fun `a 201 envelope with no data is ambiguous, not unexpected`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"success":true,"message":"Created as a draft."}"""),
        )

        val attempt = WriteAttempt()
        val result = safeWriteCall(attempt) { api.createStockAdjustment(body, attempt) }
            .mapDocument(attempt, StockAdjustmentDto::toDomain)

        assertTrue(result.exceptionOrNull() is PdaApiException.Ambiguous)
    }

    @Test
    fun `a normal 201 succeeds and carries created_at through`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"success":true,"type":"stock_adjustment","data":{"id":7,"product_id":55,
                   "adjustment_type":"Stock Increase","direction":"IN","quantity":"60.0000",
                   "portal_state":"pending","created_at":"2026-08-13T10:00:00.000000Z"}}""",
            ),
        )

        val attempt = WriteAttempt()
        val doc = safeWriteCall(attempt) { api.createStockAdjustment(body, attempt) }
            .mapDocument(attempt, StockAdjustmentDto::toDomain)
            .getOrThrow()

        assertEquals(7L, doc.id)
        assertEquals(60.0, doc.quantity, 0.001)
        // Reconciliation needs this to bound its match window.
        assertEquals(
            Instant.parse("2026-08-13T10:00:00Z").toEpochMilli(),
            doc.createdAtEpochMs,
        )
    }

    @Test
    fun `the envelope parses when the portal wraps the document normally`() {
        val envelope = PdaJson.instance.decodeFromString<DocumentEnvelope<StockAdjustmentDto>>(
            """{"success":true,"data":{"id":1,"quantity":"1.0000"}}""",
        )
        assertEquals(1L, envelope.data?.id)
    }
}
