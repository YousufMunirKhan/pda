package com.example.swtichandsavepda.presentation.screens.purchaseorder

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseOrderDoc
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.presentation.components.BrandCard
import com.example.swtichandsavepda.presentation.components.BrandScaffold
import com.example.swtichandsavepda.presentation.components.FormField
import com.example.swtichandsavepda.presentation.components.ReferenceOption
import com.example.swtichandsavepda.presentation.components.ReceiveSheet
import com.example.swtichandsavepda.presentation.components.ReferencePickerField
import com.example.swtichandsavepda.presentation.components.SectionTitle
import com.example.swtichandsavepda.presentation.components.StatusCapsule
import com.example.swtichandsavepda.presentation.components.SubmitOutcomeBanner
import com.example.swtichandsavepda.presentation.components.UnitSection
import com.example.swtichandsavepda.ui.theme.StatusDanger
import com.example.swtichandsavepda.ui.theme.StatusSuccess
import com.example.swtichandsavepda.ui.theme.TagDangerBg
import com.example.swtichandsavepda.ui.theme.TagDangerFg
import com.example.swtichandsavepda.ui.theme.TagNeutralBg
import com.example.swtichandsavepda.ui.theme.TagNeutralFg
import com.example.swtichandsavepda.ui.theme.TagSuccessBg
import com.example.swtichandsavepda.ui.theme.TagSuccessFg
import com.example.swtichandsavepda.ui.theme.TagWarningBg
import com.example.swtichandsavepda.ui.theme.TagWarningFg
import com.example.swtichandsavepda.ui.theme.brandColors
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun PurchaseOrderScreen(
    uiState: PurchaseOrderUiState,
    onSearchSuppliers: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectSupplier: (ReferenceOption) -> Unit,
    onDeliveryDateChange: (String) -> Unit,
    onSearchProducts: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectLineProduct: (ReferenceOption) -> Unit,
    onSelectLineUnit: (ProductUnit) -> Unit,
    onRetryLineUnits: () -> Unit,
    onLineQuantityChange: (String) -> Unit,
    onLineUnitCostChange: (String) -> Unit,
    onAddLine: () -> Unit,
    onRemoveLine: (Int) -> Unit,
    onSubmit: () -> Unit,
    onScanProduct: () -> Unit,
    onStartReceive: (Long) -> Unit,
    onReceiveQuantityChange: (Int, String) -> Unit,
    onReceiveDeliveryNoteChange: (String) -> Unit,
    onFillReceiveRemaining: () -> Unit,
    onConfirmReceive: () -> Unit,
    onCancelReceive: () -> Unit,
    onCancelOrder: (Long) -> Unit,
    onRefresh: () -> Unit,
    onDismissMessages: () -> Unit,
    onBackClick: () -> Unit,
) {
    // A stock write that has left the device cannot be un-sent, and leaving the
    // screen would cancel our view of it without cancelling the server's. Hold
    // the operator here until the outcome is known - the 30s timeout bounds it.
    BackHandler(enabled = uiState.isSubmitting) { /* deliberately swallowed */ }

    BrandScaffold(
        title = "Purchase Orders",
        subtitle = "Create a draft PO or receive one",
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

            CreateOrderCard(
                uiState = uiState,
                onSearchSuppliers = onSearchSuppliers,
                onSelectSupplier = onSelectSupplier,
                onDeliveryDateChange = onDeliveryDateChange,
                onSearchProducts = onSearchProducts,
                onSelectLineProduct = onSelectLineProduct,
                onSelectLineUnit = onSelectLineUnit,
                onRetryLineUnits = onRetryLineUnits,
                onLineQuantityChange = onLineQuantityChange,
                onLineUnitCostChange = onLineUnitCostChange,
                onAddLine = onAddLine,
                onRemoveLine = onRemoveLine,
                onSubmit = onSubmit,
                onScanProduct = onScanProduct,
            )

            SectionTitle(text = "Purchase orders for this shop")
            when {
                uiState.isLoading && uiState.orders.isEmpty() -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                uiState.orders.isEmpty() -> Text(
                    text = "No purchase orders yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.brandColors.textSecondary,
                )

                else -> uiState.orders.forEach { order ->
                    OrderRow(
                        order = order,
                        isBusy = uiState.busyOrderId == order.id,
                        onReceive = { onStartReceive(order.id) },
                        onCancel = { onCancelOrder(order.id) },
                    )
                }
            }
        }
    }

    uiState.receiveDraft?.let { draft ->
        ReceiveSheet(
            draft = draft,
            onQuantityChange = onReceiveQuantityChange,
            onDeliveryNoteChange = onReceiveDeliveryNoteChange,
            onFillRemaining = onFillReceiveRemaining,
            onConfirm = onConfirmReceive,
            onDismiss = onCancelReceive,
        )
    }
}

@Composable
private fun CreateOrderCard(
    uiState: PurchaseOrderUiState,
    onSearchSuppliers: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectSupplier: (ReferenceOption) -> Unit,
    onDeliveryDateChange: (String) -> Unit,
    onSearchProducts: suspend (String) -> Result<List<ReferenceOption>>,
    onSelectLineProduct: (ReferenceOption) -> Unit,
    onSelectLineUnit: (ProductUnit) -> Unit,
    onRetryLineUnits: () -> Unit,
    onLineQuantityChange: (String) -> Unit,
    onLineUnitCostChange: (String) -> Unit,
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
            SectionTitle(text = "New purchase order")

            ReferencePickerField(
                label = "Supplier",
                selected = uiState.supplier,
                onSearch = onSearchSuppliers,
                onSelected = onSelectSupplier,
                enabled = !uiState.isSubmitting,
                isError = uiState.fieldErrors.containsKey("supplier_id"),
                placeholder = "Tap to search suppliers",
            )

            DateField(
                value = uiState.deliveryDate,
                onValueChange = onDeliveryDateChange,
                enabled = !uiState.isSubmitting,
                isError = uiState.fieldErrors.containsKey("expected_delivery_date"),
            )

            SectionTitle(text = "Add a line")
            ReferencePickerField(
                label = "Product",
                selected = uiState.lineProduct,
                onSearch = onSearchProducts,
                onSelected = onSelectLineProduct,
                enabled = !uiState.isSubmitting,
                placeholder = "Search name, code or barcode",
                onScanRequested = onScanProduct,
            )
            UnitSection(
                choice = uiState.lineUnitChoice,
                onSelect = onSelectLineUnit,
                onRetry = onRetryLineUnits,
                enabled = !uiState.isSubmitting,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = uiState.lineQuantity,
                    onValueChange = onLineQuantityChange,
                    label = "Qty",
                    helper = uiState.lineBaseQuantityHint,
                    keyboardType = if (uiState.lineUnitChoice.quantityDecimals > 0) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Number
                    },
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = uiState.lineUnitCost,
                    onValueChange = onLineUnitCostChange,
                    label = uiState.lineUnitChoice.selected
                        ?.takeIf { !it.isBase }
                        ?.let { "Cost / ${it.label}" }
                        ?: "Unit cost",
                    helper = uiState.lineBaseCostHint,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = onAddLine,
                enabled = uiState.canAddLine && !uiState.isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add line")
            }

            if (uiState.draftLines.isNotEmpty()) {
                uiState.draftLines.forEachIndexed { index, line ->
                    DraftLineRow(
                        title = line.product.title,
                        detail = "${line.quantityLabel} × £${"%.2f".format(line.unitCost)}",
                        onRemove = { onRemoveLine(index) },
                    )
                }
                Text(
                    text = "${UomMath.pretty(uiState.draftUnits)} base units · " +
                        "£${"%.2f".format(uiState.draftValue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.brandColors.textHeading,
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
                        text = "Create purchase order",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean,
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Expected delivery date") },
        placeholder = { Text("YYYY-MM-DD") },
        isError = isError,
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        trailingIcon = {
            IconButton(onClick = { showPicker = true }, enabled = enabled) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.brandColors.cardStroke,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    if (showPicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { onValueChange(millisToIsoDate(it)) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

private fun millisToIsoDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

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
private fun OrderRow(
    order: PurchaseOrderDoc,
    isBusy: Boolean,
    onReceive: () -> Unit,
    onCancel: () -> Unit,
) {
    // Derived from the line quantities, never from the status string: a
    // partially-received PO reports a status containing "receiv", which under
    // the old string match hid the Receive button and closed the PO for good.
    val ordered = order.orderedTotal
    val received = order.receivedTotal
    val remaining = order.remainingTotal
    val returned = order.lines.sumOf { it.quantityReturned ?: 0.0 }

    BrandCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.reference,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.brandColors.textHeading,
                    )
                    Text(
                        text = buildString {
                            append(order.supplierName ?: "Supplier ${order.supplierId ?: "?"}")
                            append(" · ${order.lines.size} line(s)")
                            order.expectedDeliveryDate?.let { append(" · due ${it.take(10)}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.brandColors.textSecondary,
                    )
                }
                OrderStatusChip(status = order.status)
            }

            // Ordered / Received / Returned / Remaining, so a part-delivered PO
            // reads at a glance without opening it.
            if (received > 0 || returned > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("Ordered ${UomMath.pretty(ordered)}")
                        append(" · received ${UomMath.pretty(received)}")
                        if (returned > 0) append(" · returned ${UomMath.pretty(returned)}")
                        append(" · ${UomMath.pretty(remaining)} outstanding")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (order.isFullyReceived) StatusSuccess else MaterialTheme.colorScheme.primary,
                )
            }

            // POS confirmation state (separate from the PO status above).
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when (order.portalState) {
                    PortalState.CONFIRMED -> "Confirmed by POS"
                    PortalState.REJECTED -> "Rejected by POS"
                    else -> "Awaiting POS confirmation"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textTertiary,
            )

            order.rejectReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Reason: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusDanger,
                )
            }

            // Actions while anything is still outstanding. A partially-received
            // PO stays receivable — that is the whole point of partial receipts.
            if (order.canReceive) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onReceive, enabled = !isBusy) {
                        Text(if (received > 0) "Receive more" else "Receive")
                    }
                    TextButton(onClick = onCancel, enabled = !isBusy) {
                        Text("Cancel", color = StatusDanger)
                    }
                }
            }
        }
    }
}

/** The PO's own lifecycle (Pending → Received → Cancelled), coloured. */
@Composable
private fun OrderStatusChip(status: String?) {
    val label = status?.takeIf { it.isNotBlank() } ?: "Draft"
    val key = label.lowercase()
    val (background, foreground) = when {
        key.contains("cancel") -> TagDangerBg to TagDangerFg
        key.contains("receiv") -> TagSuccessBg to TagSuccessFg
        key.contains("pending") || key.contains("draft") -> TagWarningBg to TagWarningFg
        else -> TagNeutralBg to TagNeutralFg
    }
    StatusCapsule(label = label, background = background, foreground = foreground)
}
