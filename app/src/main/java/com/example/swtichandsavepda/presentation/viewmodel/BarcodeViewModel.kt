package com.example.swtichandsavepda.presentation.screens.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.model.BarcodeMatch
import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The product a completed scan hands to the next screen, with the unit the
 * barcode identified. [unit] is null for a legacy single-unit product.
 */
data class ScannedTarget(
    val product: ProductRef,
    val unit: ProductUnit? = null,
)

/** What a scan resolved to, driving the action sheet on the scanner. */
sealed interface ScanOutcome {
    data class Found(val target: ScannedTarget) : ScanOutcome

    /** One barcode registered against several units — the operator picks which. */
    data class MultipleUnits(val barcode: String, val matches: List<BarcodeMatch>) : ScanOutcome

    data class NotFound(val barcode: String) : ScanOutcome
    data class Error(val barcode: String, val message: String) : ScanOutcome
}

data class BarcodeUiState(
    val isResolving: Boolean = false,
    val outcome: ScanOutcome? = null,
    val scanCount: Int = 0,
)

/**
 * Scanner hub: turns a scanned/typed code into a product via the portal's
 * Multi-UOM resolver (`GET /api/pda/resolve-barcode`), then presents the
 * next-action sheet. It does not mutate stock itself — the chosen action routes
 * to the relevant feature screen with the product and unit pre-selected.
 */
@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val referenceRepository: PdaReferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BarcodeUiState())
    val uiState: StateFlow<BarcodeUiState> = _uiState.asStateFlow()

    /**
     * Guards against re-resolving while a lookup is in flight or the result
     * sheet is already open — the camera re-detects the same label every frame.
     */
    private var isBusy = false

    /** Continuous camera detections. Ignored while a result is on screen. */
    fun onBarcodeDetected(barcode: String) {
        if (isBusy || _uiState.value.outcome != null) return
        resolve(barcode)
    }

    /** Manual entry / test path. */
    fun lookup(barcode: String) {
        if (isBusy) return
        resolve(barcode)
    }

    private fun resolve(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return

        isBusy = true
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true) }

            val outcome = resolveWithUnits(trimmed)

            _uiState.update {
                it.copy(
                    isResolving = false,
                    outcome = outcome,
                    // Every resolved scan counts — the label reads "N scanned".
                    scanCount = it.scanCount + 1,
                )
            }
            isBusy = false
        }
    }

    /**
     * Multi-UOM resolution with a graceful path back to the plain product
     * lookup: a product the portal has no unit rows for returns no matches, and
     * a portal that predates `resolve-barcode` answers 404. Neither should stop
     * an operator scanning a legacy product.
     */
    private suspend fun resolveWithUnits(barcode: String): ScanOutcome =
        referenceRepository.resolveBarcode(barcode).fold(
            onSuccess = { matches ->
                when (matches.size) {
                    0 -> lookupWithoutUnits(barcode)
                    1 -> ScanOutcome.Found(matches.first().toTarget())
                    else -> ScanOutcome.MultipleUnits(barcode, matches)
                }
            },
            onFailure = { throwable ->
                if (throwable is PdaApiException.NotFound) {
                    lookupWithoutUnits(barcode)
                } else {
                    ScanOutcome.Error(barcode, throwable.message ?: "Lookup failed. Try again.")
                }
            },
        )

    /** The pre-Multi-UOM path: exact barcode match on the product list. */
    private suspend fun lookupWithoutUnits(barcode: String): ScanOutcome =
        referenceRepository.findProductByBarcode(barcode).fold(
            onSuccess = { product ->
                if (product != null) {
                    ScanOutcome.Found(ScannedTarget(product))
                } else {
                    ScanOutcome.NotFound(barcode)
                }
            },
            onFailure = { throwable ->
                ScanOutcome.Error(barcode, throwable.message ?: "Lookup failed. Try again.")
            },
        )

    /** The operator picked one of several units the scanned barcode matched. */
    fun selectMatch(match: BarcodeMatch) {
        _uiState.update { it.copy(outcome = ScanOutcome.Found(match.toTarget())) }
    }

    /** Clears the result sheet so scanning can resume. */
    fun dismissOutcome() {
        _uiState.update { it.copy(outcome = null) }
    }
}

/**
 * A resolve-barcode row is already product + unit; the prices it carries are the
 * *selected unit's*, which is exactly what the destination form should prefill.
 */
private fun BarcodeMatch.toTarget(): ScannedTarget = ScannedTarget(
    product = ProductRef(
        id = productId,
        name = productName,
        barcode = matchedBarcode,
        code = null,
        cost = unit.purchaseCost,
        retail = unit.retailPrice,
        unitType = unit.code,
    ),
    unit = unit,
)
