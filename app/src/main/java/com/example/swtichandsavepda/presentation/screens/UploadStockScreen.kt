package com.example.swtichandsavepda.presentation.screens.uploadstock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.presentation.components.BrandCard
import com.example.swtichandsavepda.presentation.components.BrandScaffold
import com.example.swtichandsavepda.presentation.components.FeedbackBanner
import com.example.swtichandsavepda.presentation.components.FormField
import com.example.swtichandsavepda.presentation.components.ReferenceOption
import com.example.swtichandsavepda.presentation.components.ReferencePickerField
import com.example.swtichandsavepda.presentation.components.UnitSelectorRow
import com.example.swtichandsavepda.ui.theme.brandColors

@Composable
fun UploadStockScreen(
    uiState: UploadStockUiState,
    onSearchProducts: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectProduct: (ReferenceOption) -> Unit,
    onSelectUnit: (ProductUnit) -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitCostChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onScanProduct: () -> Unit,
    onDismissMessages: () -> Unit,
    onBackClick: () -> Unit,
) {
    BrandScaffold(
        title = "Upload New Stock",
        subtitle = "Book in goods received",
        onBackClick = onBackClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.successMessage?.let { message ->
                FeedbackBanner(message = message, isError = false, onDismiss = onDismissMessages)
            }
            uiState.error?.let { message ->
                FeedbackBanner(message = message, isError = true, onDismiss = onDismissMessages)
            }

            Text(
                text = "Creates a stock-increase draft. The POS performs the FIFO " +
                    "receipt when it confirms it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.brandColors.textSecondary,
            )

            BrandCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ReferencePickerField(
                        label = "Product",
                        selected = uiState.product,
                        onSearch = onSearchProducts,
                        onSelected = onSelectProduct,
                        enabled = !uiState.isSubmitting,
                        isError = uiState.fieldErrors.containsKey("product_id"),
                        placeholder = "Search name, code or barcode",
                        onScanRequested = onScanProduct,
                    )

                    UnitSelectorRow(
                        choice = uiState.unitChoice,
                        onSelect = onSelectUnit,
                        enabled = !uiState.isSubmitting,
                    )

                    FormField(
                        value = uiState.quantity,
                        onValueChange = onQuantityChange,
                        label = "Quantity received",
                        helper = uiState.baseQuantityHint,
                        isError = uiState.fieldErrors.containsKey("quantity"),
                        enabled = !uiState.isSubmitting,
                        keyboardType = if (uiState.unitChoice.quantityDecimals > 0) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        },
                    )

                    FormField(
                        value = uiState.unitCost,
                        onValueChange = onUnitCostChange,
                        label = "Unit cost",
                        helper = "Required, must be greater than 0.",
                        isError = uiState.fieldErrors.containsKey("unit_cost"),
                        enabled = !uiState.isSubmitting,
                        keyboardType = KeyboardType.Decimal,
                    )

                    FormField(
                        value = uiState.reason,
                        onValueChange = onReasonChange,
                        label = "Reference / note (optional)",
                        enabled = !uiState.isSubmitting,
                        imeAction = ImeAction.Done,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Button(
                        onClick = onSubmit,
                        enabled = uiState.canSubmit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = "Book in stock",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
