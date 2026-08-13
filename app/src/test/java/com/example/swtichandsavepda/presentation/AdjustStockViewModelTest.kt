package com.example.swtichandsavepda.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.swtichandsavepda.MainDispatcherRule
import com.example.swtichandsavepda.data.local.AttemptState
import com.example.swtichandsavepda.data.local.SubmissionAttempt
import com.example.swtichandsavepda.data.local.SubmissionJournal
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        /** Holds every create open until completed, for the double-submit test. */
        val gate: CompletableDeferred<Unit>? = null,
    ) : PdaStockAdjustmentRepository {
        var lastCreated: NewStockAdjustment? = null
        var createCallCount = 0

        override suspend fun create(adjustment: NewStockAdjustment): Result<StockAdjustmentDoc> {
            createCallCount++
            lastCreated = adjustment
            gate?.await()
            return createResult
        }

        override suspend fun edit(id: Long, adjustment: NewStockAdjustment) = createResult
        override suspend fun list(): Result<List<StockAdjustmentDoc>> = Result.success(emptyList())

        override suspend fun cancel(id: Long): Result<StockAdjustmentDoc> {
            gate?.await()
            return createResult
        }

        companion object {
            val SAMPLE = StockAdjustmentDoc(
                id = 1, productId = 55, productName = null, adjustmentType = "Stock Decrease",
                direction = "OUT", quantity = 2.0, unitCost = null, reason = null,
                portalState = PortalState.PENDING, rejectReason = null,
            )
        }
    }

    /**
     * [units] gives a product a Multi-UOM unit list; empty = a genuine
     * single-unit product. [unitsFail] simulates the lookup failing, which must
     * NOT be indistinguishable from single-unit.
     */
    private class FakeReferenceRepository(
        private val units: List<ProductUnit> = emptyList(),
        private val unitsFail: Boolean = false,
        /** Holds the units lookup open, so Loading is observable. */
        private val unitsGate: CompletableDeferred<Unit>? = null,
    ) : PdaReferenceRepository {
        override suspend fun searchProducts(query: String) = Result.success(emptyList<ProductRef>())
        override suspend fun findProductByBarcode(barcode: String) = Result.success<ProductRef?>(null)
        override suspend fun resolveBarcode(barcode: String) = Result.success(emptyList<BarcodeMatch>())
        override suspend fun productUnits(productId: Long): Result<List<ProductUnit>> {
            unitsGate?.await()
            return if (unitsFail) {
                Result.failure(PdaApiException.Network("offline"))
            } else {
                Result.success(units)
            }
        }
        override suspend fun searchSuppliers(query: String) = Result.success(emptyList<SupplierRef>())
        override suspend fun searchLocations(query: String) = Result.success(emptyList<StockLocationRef>())
    }

    private class FakeJournal : SubmissionJournal {
        val rows = mutableListOf<SubmissionAttempt>()
        override suspend fun begin(
            id: String,
            documentType: String,
            summary: String,
            startedAtEpochMs: Long,
        ) {
            rows += SubmissionAttempt(id, documentType, summary, startedAtEpochMs, AttemptState.SENDING)
        }

        override suspend fun resolve(id: String, state: AttemptState, portalDocumentId: Long?) {
            rows.replaceAll { if (it.id == id) it.copy(state = state) else it }
        }

        override suspend fun forget(id: String) = Unit
        override suspend fun markOrphansUnknown() = Unit
        override suspend fun unresolved() = rows.filter { it.state == AttemptState.UNKNOWN }
    }

    private fun unit(id: Long, code: String, conversion: Double) = ProductUnit(
        productUnitId = id,
        selectedUnitId = id + 100,
        code = code,
        name = code,
        barcode = null,
        conversionToBase = conversion,
        retailPrice = null,
        purchaseCost = null,
        isBaseUnit = conversion == 1.0,
        allowDecimal = false,
        decimalPlaces = 0,
    )

    private fun product(id: Long) = ReferenceOption(id = id, title = "Product $id")
    private fun location(id: Long) = ReferenceOption(id = id, title = "Location $id")

    private fun TestScope.viewModel(
        repository: PdaStockAdjustmentRepository = FakeAdjustmentRepository(),
        reference: PdaReferenceRepository = FakeReferenceRepository(),
        journal: SubmissionJournal = FakeJournal(),
    ) = AdjustStockViewModel(repository, reference, journal, this, SavedStateHandle())

    @Test
    fun `valid decrease builds the documented request and reports success`() = runTest {
        val repository = FakeAdjustmentRepository()
        val viewModel = viewModel(repository)

        viewModel.selectMode(AdjustmentMode.DECREASE)
        viewModel.selectProduct(product(55))
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))

        assertTrue(viewModel.uiState.value.canSubmit)
        viewModel.submit()
        advanceUntilIdle()

        val sent = requireNotNull(repository.lastCreated)
        assertEquals(55L, sent.productId)
        assertEquals(AdjustmentMode.DECREASE, sent.mode)
        assertEquals(1L, sent.sourceLocationId)
        assertNull(sent.unitCost) // decrease must not carry a unit cost
        assertTrue(viewModel.uiState.value.outcome is SubmitOutcome.Created)
        assertNull(viewModel.uiState.value.product) // form cleared
    }

    @Test
    fun `location move blocks identical source and destination`() = runTest {
        val viewModel = viewModel()

        viewModel.selectMode(AdjustmentMode.LOCATION_MOVE)
        viewModel.selectProduct(product(55))
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))
        viewModel.selectDestinationLocation(location(1))
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.selectDestinationLocation(location(2))
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    // ── Multi-UOM ───────────────────────────────────────────────────────────

    @Test
    fun `a multi-unit product sends the entered quantity with its unit`() = runTest {
        val box = unit(id = 3, code = "BOX", conversion = 12.0)
        val repository = FakeAdjustmentRepository()
        val viewModel = viewModel(
            repository,
            FakeReferenceRepository(units = listOf(unit(1, "PCS", 1.0), box)),
        )

        viewModel.selectProduct(product(55))
        advanceUntilIdle()
        viewModel.selectUnit(box)
        viewModel.setQuantity("5")
        viewModel.selectSourceLocation(location(1))

        // The operator sees what the POS will actually book before submitting.
        assertEquals("= 60 PCS", viewModel.uiState.value.baseQuantityHint)

        viewModel.submit()
        advanceUntilIdle()

        val sent = requireNotNull(repository.lastCreated)
        // The ViewModel passes what the operator typed; the repository converts.
        assertEquals(5.0, sent.quantity, 0.0001)
        assertEquals(box, sent.unit)
    }

    @Test
    fun `a single-unit product sends no unit at all`() = runTest {
        val repository = FakeAdjustmentRepository()
        val viewModel = viewModel(repository)

        viewModel.selectProduct(product(55))
        advanceUntilIdle()
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))
        viewModel.submit()
        advanceUntilIdle()

        assertNull(requireNotNull(repository.lastCreated).unit)
    }

    @Test
    fun `changing unit clears the quantity typed for the previous one`() = runTest {
        val box = unit(id = 3, code = "BOX", conversion = 12.0)
        val viewModel = viewModel(
            reference = FakeReferenceRepository(units = listOf(unit(1, "PCS", 1.0), box)),
        )

        viewModel.selectProduct(product(55))
        advanceUntilIdle()
        viewModel.setQuantity("5")
        viewModel.selectUnit(box)

        // 5 Pcs must not silently become 5 Box (= 60 Pcs).
        assertEquals("", viewModel.uiState.value.quantity)
    }

    @Test
    fun `a failed units lookup blocks submit instead of booking base units`() = runTest {
        // Regression for the silent 12x error: an unresolved unit list used to be
        // indistinguishable from a single-unit product, so a quantity meaning
        // boxes was sent as pieces with no error shown to anyone.
        val repository = FakeAdjustmentRepository()
        val viewModel = viewModel(repository, FakeReferenceRepository(unitsFail = true))

        viewModel.selectProduct(product(55))
        advanceUntilIdle()
        viewModel.setQuantity("5")
        viewModel.selectSourceLocation(location(1))

        assertEquals(UnitChoice.Status.Failed, viewModel.uiState.value.unitChoice.status)
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.submit()
        advanceUntilIdle()
        assertEquals(0, repository.createCallCount)
    }

    @Test
    fun `submit is blocked while the units lookup is still in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(
            reference = FakeReferenceRepository(units = listOf(unit(1, "PCS", 1.0)), unitsGate = gate),
        )

        viewModel.selectProduct(product(55))
        viewModel.setQuantity("5")
        viewModel.selectSourceLocation(location(1))
        advanceUntilIdle()

        // Units have not come back yet: what "5" means is still unknown.
        assertEquals(UnitChoice.Status.Loading, viewModel.uiState.value.unitChoice.status)
        assertFalse(viewModel.uiState.value.canSubmit)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    // ── Duplicate-write protection ──────────────────────────────────────────

    @Test
    fun `two taps in the same frame produce exactly one write`() = runTest(StandardTestDispatcher()) {
        // Deliberately a non-eager dispatcher: the old read-then-launch guard
        // only held because viewModelScope is Main.immediate, and the write now
        // runs on an application scope instead.
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAdjustmentRepository(gate = gate)
        val viewModel = viewModel(repository)

        viewModel.selectProduct(product(55))
        advanceUntilIdle()
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))

        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.createCallCount)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `an ambiguous write is not offered a retry and clears the form`() = runTest {
        val repository = FakeAdjustmentRepository(
            createResult = Result.failure(
                PdaApiException.Ambiguous("The connection dropped after this was sent.", 0L),
            ),
        )
        val journal = FakeJournal()
        val viewModel = viewModel(repository, journal = journal)

        viewModel.selectProduct(product(55))
        advanceUntilIdle()
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.outcome is SubmitOutcome.Unresolved)
        // The form must not be left primed to send the same movement again.
        assertNull(state.product)
        assertEquals("", state.quantity)
        assertFalse(state.canSubmit)
        // And it is recorded, so the menu can surface it.
        assertEquals(AttemptState.UNKNOWN, journal.rows.single().state)
    }

    @Test
    fun `a definite failure keeps the form so the operator can retry`() = runTest {
        val repository = FakeAdjustmentRepository(
            createResult = Result.failure(
                PdaApiException.Validation("Check the form", mapOf("quantity" to listOf("must be > 0"))),
            ),
        )
        val journal = FakeJournal()
        val viewModel = viewModel(repository, journal = journal)

        viewModel.selectProduct(product(55))
        advanceUntilIdle()
        viewModel.setQuantity("2")
        viewModel.selectSourceLocation(location(1))
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.outcome is SubmitOutcome.Failed)
        assertEquals("Check the form", state.outcome?.message)
        assertTrue(state.fieldErrors.containsKey("quantity"))
        // Nothing was created, so the entered values stay put.
        assertEquals("2", state.quantity)
        assertEquals(AttemptState.NOT_CREATED, journal.rows.single().state)
    }

    @Test
    fun `a second cancel cannot clobber the first one's in-flight marker`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAdjustmentRepository(gate = gate)
        val viewModel = viewModel(repository)

        viewModel.cancel(1)
        viewModel.cancel(2)
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.cancellingId)
        gate.complete(Unit)
        advanceUntilIdle()
    }
}
