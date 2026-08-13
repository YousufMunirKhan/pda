package com.example.swtichandsavepda.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.swtichandsavepda.MainDispatcherRule
import com.example.swtichandsavepda.data.model.AdjustmentMode
import com.example.swtichandsavepda.data.model.BarcodeMatch
import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.StockAdjustmentDoc
import com.example.swtichandsavepda.data.model.StockLocationRef
import com.example.swtichandsavepda.data.model.SupplierRef
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
import com.example.swtichandsavepda.data.repository.PdaStockAdjustmentRepository
import com.example.swtichandsavepda.presentation.components.ReferenceOption
import com.example.swtichandsavepda.presentation.screens.adjuststock.AdjustStockViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdjustStockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAdjustmentRepository(
        var createResult: Result<StockAdjustmentDoc> = Result.success(SAMPLE),
    ) : PdaStockAdjustmentRepository {
        var lastCreated: NewStockAdjustment? = null

        override suspend fun create(adjustment: NewStockAdjustment): Result<StockAdjustmentDoc> {
            lastCreated = adjustment
            return createResult
        }

        override suspend fun edit(id: Long, adjustment: NewStockAdjustment) = createResult
        override suspend fun list(): Result<List<StockAdjustmentDoc>> = Result.success(emptyList())
        override suspend fun cancel(id: Long) = createResult

        companion object {
            val SAMPLE = StockAdjustmentDoc(
                id = 1, productId = 55, productName = null, adjustmentType = "Stock Decrease",
                direction = "OUT", quantity = 2.0, unitCost = null, reason = null,
                portalState = PortalState.PENDING, rejectReason = null,
            )
        }
    }

    /** [units] lets a test give a product a Multi-UOM unit list; empty = legacy. */
    private class FakeReferenceRepository(
        private val units: List<ProductUnit> = emptyList(),
    ) : PdaReferenceRepository {
        override suspend fun searchProducts(query: String) = Result.success(emptyList<ProductRef>())
        override suspend fun findProductByBarcode(barcode: String) = Result.success<ProductRef?>(null)
        override suspend fun resolveBarcode(barcode: String) = Result.success(emptyList<BarcodeMatch>())
        override suspend fun productUnits(productId: Long) = Result.success(units)
        override suspend fun searchSuppliers(query: String) = Result.success(emptyList<SupplierRef>())
        override suspend fun searchLocations(query: String) = Result.success(emptyList<StockLocationRef>())
    }

    private fun unit(
        id: Long,
        code: String,
        conversion: Double,
        isBase: Boolean = conversion == 1.0,
    ) = ProductUnit(
        productUnitId = id,
        selectedUnitId = id + 100,
        code = code,
        name = code,
        barcode = null,
        conversionToBase = conversion,
        retailPrice = null,
        purchaseCost = null,
        isBaseUnit = isBase,
        allowDecimal = false,
        decimalPlaces = 0,
    )

    private fun product(id: Long) = ReferenceOption(id = id, title = "Product $id")
    private fun location(id: Long) = ReferenceOption(id = id, title = "Location $id")

    @Test
    fun `valid decrease builds the documented request and reports success`() = runTest {
        val repository = FakeAdjustmentRepository()
        val viewModel = AdjustStockViewModel(repository, FakeReferenceRepository(), SavedStateHandle())

        viewModel.selectMode(AdjustmentMode.DECREASE)
        viewModel.selectProduct(product(55))
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))

        assertTrue(viewModel.uiState.value.canSubmit)
        viewModel.submit()

        val sent = requireNotNull(repository.lastCreated)
        assertEquals(55L, sent.productId)
        assertEquals(AdjustmentMode.DECREASE, sent.mode)
        assertEquals(1L, sent.sourceLocationId)
        assertNull(sent.unitCost) // decrease must not carry a unit cost
        assertNotNull(viewModel.uiState.value.successMessage)
        assertNull(viewModel.uiState.value.product) // form cleared
    }

    @Test
    fun `location move blocks identical source and destination`() = runTest {
        val viewModel = AdjustStockViewModel(
            FakeAdjustmentRepository(),
            FakeReferenceRepository(),
            SavedStateHandle(),
        )

        viewModel.selectMode(AdjustmentMode.LOCATION_MOVE)
        viewModel.selectProduct(product(55))
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))
        viewModel.selectDestinationLocation(location(1))
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.selectDestinationLocation(location(2))
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `a multi-unit product sends the entered quantity with its unit`() = runTest {
        val box = unit(id = 3, code = "BOX", conversion = 12.0)
        val repository = FakeAdjustmentRepository()
        val viewModel = AdjustStockViewModel(
            repository,
            FakeReferenceRepository(units = listOf(unit(1, "PCS", 1.0), box)),
            SavedStateHandle(),
        )

        viewModel.selectProduct(product(55))
        viewModel.selectUnit(box)
        viewModel.setQuantity("5")
        viewModel.selectSourceLocation(location(1))

        // The operator sees what the POS will actually book before submitting.
        assertEquals("= 60 PCS", viewModel.uiState.value.baseQuantityHint)

        viewModel.submit()

        val sent = requireNotNull(repository.lastCreated)
        // The ViewModel passes what the operator typed; the repository converts.
        assertEquals(5.0, sent.quantity, 0.0001)
        assertEquals(box, sent.unit)
    }

    @Test
    fun `a single-unit product sends no unit at all`() = runTest {
        val repository = FakeAdjustmentRepository()
        val viewModel = AdjustStockViewModel(repository, FakeReferenceRepository(), SavedStateHandle())

        viewModel.selectProduct(product(55))
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))
        viewModel.submit()

        assertNull(requireNotNull(repository.lastCreated).unit)
    }

    @Test
    fun `changing unit clears the quantity typed for the previous one`() = runTest {
        val box = unit(id = 3, code = "BOX", conversion = 12.0)
        val viewModel = AdjustStockViewModel(
            FakeAdjustmentRepository(),
            FakeReferenceRepository(units = listOf(unit(1, "PCS", 1.0), box)),
            SavedStateHandle(),
        )

        viewModel.selectProduct(product(55))
        viewModel.setQuantity("5")
        viewModel.selectUnit(box)

        // 5 Pcs must not silently become 5 Box (= 60 Pcs).
        assertEquals("", viewModel.uiState.value.quantity)
    }

    @Test
    fun `validation failure surfaces field errors`() = runTest {
        val repository = FakeAdjustmentRepository(
            createResult = Result.failure(
                PdaApiException.Validation("Check the form", mapOf("quantity" to listOf("must be > 0"))),
            ),
        )
        val viewModel = AdjustStockViewModel(repository, FakeReferenceRepository(), SavedStateHandle())

        viewModel.selectProduct(product(55))
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))
        viewModel.submit()

        assertEquals("Check the form", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.fieldErrors.containsKey("quantity"))
    }
}
