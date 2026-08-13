package com.example.swtichandsavepda.presentation.screens.adjuststock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.AdjustmentMode
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.StockAdjustmentDoc
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.presentation.components.BrandCard
import com.example.swtichandsavepda.presentation.components.BrandScaffold
import com.example.swtichandsavepda.presentation.components.FormField
import com.example.swtichandsavepda.presentation.components.PortalStateChip
import com.example.swtichandsavepda.presentation.components.ReferenceOption
import com.example.swtichandsavepda.presentation.components.ReferencePickerField
import com.example.swtichandsavepda.presentation.components.SectionTitle
import com.example.swtichandsavepda.presentation.components.SubmitOutcomeBanner
import com.example.swtichandsavepda.presentation.components.UnitSection
import com.example.swtichandsavepda.ui.theme.StatusDanger
import com.example.swtichandsavepda.ui.theme.brandColors

@Composable
fun AdjustStockScreen(
    uiState: AdjustStockUiState,
    onSelectMode: (AdjustmentMode) -> Unit,
    onSearchProducts: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectProduct: (ReferenceOption) -> Unit,
    onSelectUnit: (ProductUnit) -> Unit,
    onRetryUnits: () -> Unit,
    onQuantityChange: (String) -> Unit,
    onSearchLocations: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectSourceLocation: (ReferenceOption) -> Unit,
    onSelectDestinationLocation: (ReferenceOption) -> Unit,
    onDestinationShopChange: (String) -> Unit,
    onUnitCostChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onScanProduct: () -> Unit,
    onCancelRecent: (Long) -> Unit,
    onRefresh: () -> Unit,
    onDismissMessages: () -> Unit,
    onBackClick: () -> Unit,
) {
    // A stock write that has left the device cannot be un-sent, and leaving the
    // screen would cancel our view of it without cancelling the server's. Hold
    // the operator here until the outcome is known — the 30s timeout bounds it.
    BackHandler(enabled = uiState.isSubmitting) { /* deliberately swallowed */ }

    BrandScaffold(
        title = "Adjust Stock",
        subtitle = "Create a stock adjustment draft",
        onBackClick = onBackClick.takeIf { !uiState.isSubmitting },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.outcome?.let { outcome ->
                SubmitOutcomeBanner(outcome = outcome, onDismiss = onDismissMessages)
            }

            BrandCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModeSelector(
                        mode = uiState.mode,
                        onSelect = onSelectMode,
                        enabled = !uiState.isSubmitting,
                    )

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

                    UnitSection(
                        choice = uiState.unitChoice,
                        onSelect = onSelectUnit,
                        onRetry = onRetryUnits,
                        enabled = !uiState.isSubmitting,
                    )

                    FormField(
                        value = uiState.quantity,
                        onValueChange = onQuantityChange,
                        label = "Quantity",
                        helper = uiState.baseQuantityHint,
                        isError = uiState.fieldErrors.containsKey("quantity"),
                        enabled = !uiState.isSubmitting,
                        keyboardType = if (uiState.unitChoice.quantityDecimals > 0) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        },
                    )

                    if (uiState.mode.requiresSourceLocation) {
                        ReferencePickerField(
                            label = "Source location",
                            selected = uiState.sourceLocation,
                            onSearch = onSearchLocations,
                            onSelected = onSelectSourceLocation,
                            enabled = !uiState.isSubmitting,
                            isError = uiState.fieldErrors.containsKey("source_location_id") ||
                                uiState.locationsClash,
                            placeholder = "Tap to search locations",
                        )
                    }

                    if (uiState.mode.requiresDestinationLocation) {
                        ReferencePickerField(
                            label = "Destination location",
                            selected = uiState.destinationLocation,
                            onSearch = onSearchLocations,
                            onSelected = onSelectDestinationLocation,
                            enabled = !uiState.isSubmitting,
                            isError = uiState.fieldErrors.containsKey("destination_location_id") ||
                                uiState.locationsClash,
                            placeholder = "Tap to search locations",
                        )
                        if (uiState.locationsClash) {
                            Text(
                                text = "Source and destination must differ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    if (uiState.mode.requiresDestinationShop) {
                        FormField(
                            value = uiState.destinationShopId,
                            onValueChange = onDestinationShopChange,
                            label = "Destination shop ID",
                            helper = "Must differ from your current shop.",
                            isError = uiState.fieldErrors.containsKey("destination_shop_id"),
                            enabled = !uiState.isSubmitting,
                            keyboardType = KeyboardType.Number,
                        )
                    }

                    if (uiState.mode.requiresUnitCost) {
                        FormField(
                            value = uiState.unitCost,
                            onValueChange = onUnitCostChange,
                            label = uiState.unitChoice.selected
                                ?.takeIf { !it.isBase }
                                ?.let { "Cost per ${it.label}" }
                                ?: "Unit cost",
                            helper = uiState.baseCostHint ?: "Required, must be greater than 0.",
                            isError = uiState.fieldErrors.containsKey("unit_cost"),
                            enabled = !uiState.isSubmitting,
                            keyboardType = KeyboardType.Decimal,
                        )
                    }

                    FormField(
                        value = uiState.reason,
                        onValueChange = onReasonChange,
                        label = "Reason (optional)",
                        enabled = !uiState.isSubmitting,
                        imeAction = ImeAction.Done,
                    )

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
                                text = "Create adjustment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            RecentAdjustments(
                items = uiState.recent,
                isLoading = uiState.isLoadingRecent,
                cancellingId = uiState.cancellingId,
                onCancel = onCancelRecent,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    mode: AdjustmentMode,
    onSelect: (AdjustmentMode) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = mode.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Adjustment type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.brandColors.cardStroke,
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AdjustmentMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RecentAdjustments(
    items: List<StockAdjustmentDoc>,
    isLoading: Boolean,
    cancellingId: Long?,
    onCancel: (Long) -> Unit,
) {
    SectionTitle(text = "Recent adjustments")
    Spacer(modifier = Modifier.height(4.dp))

    when {
        isLoading && items.isEmpty() -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        items.isEmpty() -> Text(
            text = "No adjustments yet for this shop.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.brandColors.textSecondary,
        )

        else -> items.forEach { doc ->
            AdjustmentRow(
                doc = doc,
                isCancelling = cancellingId == doc.id,
                onCancel = { onCancel(doc.id) },
            )
        }
    }
}

@Composable
private fun AdjustmentRow(
    doc: StockAdjustmentDoc,
    isCancelling: Boolean,
    onCancel: () -> Unit,
) {
    BrandCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${doc.id} · ${doc.adjustmentType ?: "Adjustment"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.brandColors.textHeading,
                    )
                    Text(
                        text = buildString {
                            append("Product ${doc.productId ?: "?"} · qty ${UomMath.pretty(doc.quantity)}")
                            // A non-base unit was used: show what was entered too.
                            doc.selectedUnitCode?.let { code ->
                                val entered = doc.enteredQuantity?.let(UomMath::pretty)
                                append(if (entered != null) " ($entered $code)" else " ($code)")
                            }
                            doc.productName?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.brandColors.textSecondary,
                    )
                }
                PortalStateChip(state = doc.portalState)
            }

            doc.rejectReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Rejected: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusDanger,
                )
            }

            if (doc.portalState == PortalState.PENDING) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onCancel, enabled = !isCancelling) {
                        if (isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = StatusDanger,
                            )
                        } else {
                            Text(text = "Cancel", color = StatusDanger)
                        }
                    }
                }
            }
        }
    }
}
