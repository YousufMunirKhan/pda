package com.example.swtichandsavepda.presentation.screens.purchasereturn

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseReturnDoc
import com.example.swtichandsavepda.data.model.ReturnableLine
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.presentation.components.BrandCard
import com.example.swtichandsavepda.presentation.components.BrandScaffold
import com.example.swtichandsavepda.presentation.components.FormField
import com.example.swtichandsavepda.presentation.components.PortalStateChip
import com.example.swtichandsavepda.presentation.components.ReferenceOption
import com.example.swtichandsavepda.presentation.components.ReferencePickerField
import com.example.swtichandsavepda.presentation.components.ReturnableLinePicker
import com.example.swtichandsavepda.presentation.components.SectionTitle
import com.example.swtichandsavepda.presentation.components.SubmitOutcomeBanner
import com.example.swtichandsavepda.presentation.components.UnitSection
import com.example.swtichandsavepda.ui.theme.StatusDanger
import com.example.swtichandsavepda.ui.theme.brandColors

@Composable
fun PurchaseReturnScreen(
    uiState: PurchaseReturnUiState,
    onSearchPurchaseOrders: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectPurchaseOrder: (ReferenceOption) -> Unit,
    onClearPurchaseOrder: () -> Unit,
    onSelectReturnableLine: (ReturnableLine) -> Unit,
    onSearchSuppliers: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectSupplier: (ReferenceOption) -> Unit,
    onReferenceChange: (String) -> Unit,
    onReturnReasonChange: (String) -> Unit,
    onSearchProducts: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectLineProduct: (ReferenceOption) -> Unit,
    onSelectLineUnit: (ProductUnit) -> Unit,
    onRetryLineUnits: () -> Unit,
    onLineQuantityChange: (String) -> Unit,
    onLineCostPriceChange: (String) -> Unit,
    onLineReasonChange: (String) -> Unit,
    onAddLine: () -> Unit,
    onRemoveLine: (Int) -> Unit,
    onSubmit: () -> Unit,
    onScanProduct: () -> Unit,
    onCancelReturn: (Long) -> Unit,
    onRefresh: () -> Unit,
    onDismissMessages: () -> Unit,
    onBackClick: () -> Unit,
) {
    // A stock write that has left the device cannot be un-sent, and leaving the
    // screen would cancel our view of it without cancelling the server's. Hold
    // the operator here until the outcome is known - the 30s timeout bounds it.
    BackHandler(enabled = uiState.isSubmitting) { /* deliberately swallowed */ }

    BrandScaffold(
        title = "Purchase Returns",
        subtitle = "Return goods to a supplier",
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

            CreateReturnCard(
                uiState = uiState,
                onSearchPurchaseOrders = onSearchPurchaseOrders,
                onSelectPurchaseOrder = onSelectPurchaseOrder,
                onClearPurchaseOrder = onClearPurchaseOrder,
                onSelectReturnableLine = onSelectReturnableLine,
                onSearchSuppliers = onSearchSuppliers,
                onSelectSupplier = onSelectSupplier,
                onReferenceChange = onReferenceChange,
                onReturnReasonChange = onReturnReasonChange,
                onSearchProducts = onSearchProducts,
                onSelectLineProduct = onSelectLineProduct,
                onSelectLineUnit = onSelectLineUnit,
                onRetryLineUnits = onRetryLineUnits,
                onLineQuantityChange = onLineQuantityChange,
                onLineCostPriceChange = onLineCostPriceChange,
                onLineReasonChange = onLineReasonChange,
                onAddLine = onAddLine,
                onRemoveLine = onRemoveLine,
                onSubmit = onSubmit,
                onScanProduct = onScanProduct,
            )

            SectionTitle(text = "Purchase returns for this shop")
            when {
                uiState.isLoading && uiState.returns.isEmpty() -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                uiState.returns.isEmpty() -> Text(
                    text = "No purchase returns yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.brandColors.textSecondary,
                )

                else -> uiState.returns.forEach { doc ->
                    ReturnRow(
                        doc = doc,
                        isBusy = uiState.busyReturnId == doc.id,
                        onCancel = { onCancelReturn(doc.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateReturnCard(
    uiState: PurchaseReturnUiState,
    onSearchPurchaseOrders: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectPurchaseOrder: (ReferenceOption) -> Unit,
    onClearPurchaseOrder: () -> Unit,
    onSelectReturnableLine: (ReturnableLine) -> Unit,
    onSearchSuppliers: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectSupplier: (ReferenceOption) -> Unit,
    onReferenceChange: (String) -> Unit,
    onReturnReasonChange: (String) -> Unit,
    onSearchProducts: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectLineProduct: (ReferenceOption) -> Unit,
    onSelectLineUnit: (ProductUnit) -> Unit,
    onRetryLineUnits: () -> Unit,
    onLineQuantityChange: (String) -> Unit,
    onLineCostPriceChange: (String) -> Unit,
    onLineReasonChange: (String) -> Unit,
    onAddLine: () -> Unit,
    onRemoveLine: (Int) -> Unit,
    onSubmit: () -> Unit,
    onScanProduct: () -> Unit,
) {
    BrandCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(text = "New purchase return")

            // Optional. Linking to a PO limits the return to what that delivery
            // actually brought in; leaving it empty is the plain supplier-level
            // return that existed before.
            ReferencePickerField(
                label = "Against purchase order (optional)",
                selected = uiState.purchaseOrder?.let {
                    ReferenceOption(id = it.id, title = it.reference)
                },
                onSearch = onSearchPurchaseOrders,
                onSelected = onSelectPurchaseOrder,
                enabled = !uiState.isSubmitting,
                placeholder = "Tap to search received orders",
            )

            if (uiState.isAgainstPurchaseOrder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onClearPurchaseOrder,
                        enabled = !uiState.isSubmitting,
                    ) {
                        Text("Not against an order")
                    }
                }
            }

            ReferencePickerField(
                label = "Supplier",
                selected = uiState.supplier,
                onSearch = onSearchSuppliers,
                onSelected = onSelectSupplier,
                // Taken from the PO when linked: the portal rejects a return
                // whose supplier differs from the order's.
                enabled = !uiState.isSubmitting && !uiState.isAgainstPurchaseOrder,
                isError = uiState.fieldErrors.containsKey("supplier_id"),
                placeholder = "Tap to search suppliers",
            )
            FormField(
                value = uiState.referenceNo,
                onValueChange = onReferenceChange,
                label = "Reference no.",
                isError = uiState.fieldErrors.containsKey("reference_no"),
                enabled = !uiState.isSubmitting,
            )
            FormField(
                value = uiState.returnReason,
                onValueChange = onReturnReasonChange,
                label = "Return reason",
                isError = uiState.fieldErrors.containsKey("return_reason"),
                enabled = !uiState.isSubmitting,
            )

            SectionTitle(text = "Add a line")

            if (uiState.isAgainstPurchaseOrder) {
                ReturnableLinePicker(
                    lines = uiState.returnableLines,
                    selected = uiState.selectedReturnable,
                    isLoading = uiState.isLoadingReturnable,
                    error = uiState.returnableError,
                    onSelect = onSelectReturnableLine,
                    onRetry = {
                        uiState.purchaseOrder?.let {
                            onSelectPurchaseOrder(ReferenceOption(id = it.id, title = it.reference))
                        }
                    },
                    enabled = !uiState.isSubmitting,
                )
            } else {
                ReferencePickerField(
                    label = "Product",
                    selected = uiState.lineProduct,
                    onSearch = onSearchProducts,
                    onSelected = onSelectLineProduct,
                    enabled = !uiState.isSubmitting,
                    placeholder = "Search name, code or barcode",
                    onScanRequested = onScanProduct,
                )
            }
            if (!uiState.isAgainstPurchaseOrder) {
                // A PO line already fixes the unit the goods came in on, so there
                // is nothing for the operator to choose.
                UnitSection(
                    choice = uiState.lineUnitChoice,
                    onSelect = onSelectLineUnit,
                    onRetry = onRetryLineUnits,
                    enabled = !uiState.isSubmitting,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = uiState.lineQuantity,
                    onValueChange = onLineQuantityChange,
                    label = "Qty",
                    isError = uiState.lineExceedsReturnable,
                    helper = if (uiState.lineExceedsReturnable) {
                        "Only ${UomMath.pretty(uiState.selectedReturnableRemaining)} left to return."
                    } else {
                        uiState.lineBaseQuantityHint
                    },
                    keyboardType = if (uiState.lineUnitChoice.quantityDecimals > 0) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Number
                    },
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = uiState.lineCostPrice,
                    onValueChange = onLineCostPriceChange,
                    label = uiState.lineUnitChoice.selected
                        ?.takeIf { !it.isBase }
                        ?.let { "Cost / ${it.label}" }
                        ?: "Cost price",
                    helper = uiState.lineBaseCostHint,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            FormField(
                value = uiState.lineReason,
                onValueChange = onLineReasonChange,
                label = "Line reason",
                imeAction = ImeAction.Done,
            )
            OutlinedButton(
                onClick = onAddLine,
                enabled = uiState.canAddLine && !uiState.isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add line")
            }

            uiState.draftLines.forEachIndexed { index, line ->
                DraftLineRow(
                    title = line.product.title,
                    detail = "${line.quantityLabel} × £${"%.2f".format(line.costPrice)} · ${line.reason}",
                    onRemove = { onRemoveLine(index) },
                )
            }

            Button(
                onClick = onSubmit,
                enabled = uiState.canSubmit,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                        text = "Create purchase return",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftLineRow(title: String, detail: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.brandColors.textHeading,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textSecondary,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove line",
                tint = StatusDanger,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ReturnRow(
    doc: PurchaseReturnDoc,
    isBusy: Boolean,
    onCancel: () -> Unit,
) {
    BrandCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.referenceNo,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.brandColors.textHeading,
                    )
                    Text(
                        text = buildString {
                            append(doc.supplierName ?: "Supplier ${doc.supplierId ?: "?"}")
                            append(" · ${doc.lines.size} line(s)")
                            doc.returnReason?.let { append(" · $it") }
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel, enabled = !isBusy) {
                        if (isBusy) {
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
