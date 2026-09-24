package com.example.swtichandsavepda.presentation

import com.example.swtichandsavepda.MainDispatcherRule
import com.example.swtichandsavepda.data.model.ProductPriceChange
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.remote.PdaJson
import com.example.swtichandsavepda.data.remote.dto.DocumentEnvelope
import com.example.swtichandsavepda.data.remote.dto.ProductPriceDto
import com.example.swtichandsavepda.data.remote.dto.ProductPriceRequest
import com.example.swtichandsavepda.data.repository.PdaProductPriceRepository
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PriceUpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakePriceRepository(
        var result: Result<ProductPriceChange>? = null,
    ) : PdaProductPriceRepository {
        val calls = mutableListOf<Pair<Long, Double>>()
        var lastClientReference: String? = null

        override suspend fun updateRetailPrice(
            productId: Long,
            retail: Double,
            clientReference: String?,
        ): Result<ProductPriceChange> {
            calls += productId to retail
            lastClientReference = clientReference
            return result ?: Result.success(ProductPriceChange(productId, "Coke", null, 1.99, retail))
        }
    }

    @Test
    fun `opening prefills the current price, and an unchanged price cannot be saved`() {
        val viewModel = PriceUpdateViewModel(FakePriceRepository())

        viewModel.open(productId = 55, productName = "Coke", currentRetail = 1.99)

        val editor = viewModel.uiState.value.editor!!
        assertEquals("1.99", editor.input)
        assertFalse(editor.canSave)
    }

    @Test
    fun `saving sends the new price with an idempotency key and shows old to new`() {
        val repository = FakePriceRepository()
        val viewModel = PriceUpdateViewModel(repository)
        viewModel.open(55, "Coke", 1.99)

        viewModel.setInput("2.49")
        viewModel.save()

        assertEquals(listOf(55L to 2.49), repository.calls)
        assertNotNull(repository.lastClientReference)
        val saved = viewModel.uiState.value.editor!!.saved!!
        assertEquals(1.99, saved.previousRetail!!, 0.0)
        assertEquals(2.49, saved.retail, 0.0)
    }

    @Test
    fun `input is limited to a price, two decimals at most`() {
        val viewModel = PriceUpdateViewModel(FakePriceRepository())
        viewModel.open(55, "Coke", null)

        viewModel.setInput("£2.499x")

        assertEquals("2.49", viewModel.uiState.value.editor!!.input)
    }

    @Test
    fun `an unconfirmed save tells the operator that saving again is safe`() {
        val repository = FakePriceRepository(
            result = Result.failure(PdaApiException.Ambiguous("dropped", attemptStartedAtEpochMs = 0)),
        )
        val viewModel = PriceUpdateViewModel(repository)
        viewModel.open(55, "Coke", 1.99)
        viewModel.setInput("2.49")

        viewModel.save()

        val editor = viewModel.uiState.value.editor!!
        assertNull(editor.saved)
        assertTrue(editor.error!!.contains("Saving again is safe"))
        assertTrue(editor.canSave)
    }

    @Test
    fun `the request and response match the portal contract`() {
        val body = PdaJson.instance.encodeToString(ProductPriceRequest(retail = 2.49, clientReference = "abc"))
        assertEquals("""{"retail":2.49,"client_reference":"abc"}""", body)

        val response = PdaJson.instance.decodeFromString<DocumentEnvelope<ProductPriceDto>>(
            """{"success":true,"message":"Price updated — syncing to POS.","data":{"id":55,
              "product_name":"Coke 500ml","barcode":"500000000001","previous_retail":"1.99",
              "retail":"2.49","is_synced":false}}""",
        )
        val data = response.data!!
        assertEquals(55L, data.id)
        assertEquals(1.99, data.previousRetail!!, 0.0)
        assertEquals(2.49, data.retail!!, 0.0)
    }
}
