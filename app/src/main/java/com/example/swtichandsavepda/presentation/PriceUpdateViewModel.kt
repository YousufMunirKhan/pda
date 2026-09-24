package com.example.swtichandsavepda.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.model.ProductPriceChange
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaProductPriceRepository
import com.example.swtichandsavepda.presentation.screens.adjuststock.sanitizeDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The product whose price is being edited in the dialog. */
data class PriceEditor(
    val productId: Long,
    val productName: String,
    /** Last known base retail price; null when the screen never loaded it. */
    val currentRetail: Double?,
    val input: String,
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Set once the portal accepts the price; the dialog then shows the result. */
    val saved: ProductPriceChange? = null,
) {
    val parsedInput: Double? get() = input.toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }

    val canSave: Boolean
        get() = !isSaving && saved == null && parsedInput != null && parsedInput != currentRetail
}

data class PriceUpdateUiState(val editor: PriceEditor? = null)

/**
 * Base retail price changes, reachable from every screen that moves stock.
 *
 * Its own ViewModel rather than a method on each feature's: the price change
 * is independent of the stock document being entered — it goes to a different
 * endpoint and succeeds or fails on its own — so the adjust / book-in / PO /
 * return forms stay untouched by it.
 */
@HiltViewModel
class PriceUpdateViewModel @Inject constructor(
    private val repository: PdaProductPriceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceUpdateUiState())
    val uiState: StateFlow<PriceUpdateUiState> = _uiState.asStateFlow()

    fun open(productId: Long, productName: String, currentRetail: Double?) {
        _uiState.value = PriceUpdateUiState(
            PriceEditor(
                productId = productId,
                productName = productName,
                currentRetail = currentRetail,
                input = currentRetail?.let { String.format(java.util.Locale.UK, "%.2f", it) }.orEmpty(),
            ),
        )
    }

    fun setInput(text: String) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(input = sanitizeDecimal(text), error = null))
        }
    }

    fun save() {
        val editor = _uiState.value.editor ?: return
        if (!editor.canSave) return
        val retail = editor.parsedInput ?: return
        _uiState.update { it.copy(editor = editor.copy(isSaving = true, error = null)) }

        viewModelScope.launch {
            val result = repository.updateRetailPrice(editor.productId, retail, newAttemptId())
            _uiState.update { state ->
                // Ignore a late answer for a dialog the operator closed or reopened.
                val open = state.editor?.takeIf { it.productId == editor.productId } ?: return@update state
                state.copy(
                    editor = result.fold(
                        onSuccess = { change -> open.copy(isSaving = false, saved = change) },
                        onFailure = { error -> open.copy(isSaving = false, error = error.toMessage()) },
                    ),
                )
            }
        }
    }

    fun dismiss() {
        if (_uiState.value.editor?.isSaving == true) return
        _uiState.value = PriceUpdateUiState()
    }

    /**
     * Setting a price is idempotent, so "we don't know if it saved" is resolved
     * by saving again — said plainly, unlike a stock write where re-sending
     * would duplicate a movement.
     */
    private fun Throwable.toMessage(): String = when (this) {
        is PdaApiException.Ambiguous ->
            "Couldn't confirm the new price was saved. Saving again is safe."
        is PdaApiException.NotFound -> "This product isn't on the portal for this shop."
        is PdaApiException -> message ?: "Couldn't update the price."
        else -> "Couldn't update the price. Try again."
    }
}
