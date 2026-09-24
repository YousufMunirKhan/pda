package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.presentation.ReceiveDraft
import com.example.swtichandsavepda.ui.theme.StatusSuccess
import com.example.swtichandsavepda.ui.theme.brandColors

/**
 * Goods-in for one purchase order.
 *
 * A PO is rarely delivered in one drop, so this asks "what turned up **now**"
 * per line rather than offering a single all-or-nothing Receive. Each line shows
 * what is outstanding, and a line cannot be given more than that — the remainder
 * stays open on the PO for the next delivery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveSheet(
    draft: ReceiveDraft,
    onQuantityChange: (Int, String) -> Unit,
    onDeliveryNoteChange: (String) -> Unit,
    onFillRemaining: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onUpdatePrice: (ReferenceOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = "Receive ${draft.order.reference}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.brandColors.textHeading,
            )
            Text(
                text = "${UomMath.pretty(draft.order.receivedTotal)} of " +
                    "${UomMath.pretty(draft.order.orderedTotal)} already received · " +
                    "${UomMath.pretty(draft.order.remainingTotal)} outstanding",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textSecondary,
            )

            Spacer(modifier = Modifier.height(14.dp))

            FormField(
                value = draft.deliveryNote,
                onValueChange = onDeliveryNoteChange,
                label = "Delivery note no. (optional)",
                helper = "The supplier's GRN reference — what identifies this delivery later.",
                imeAction = ImeAction.Next,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onFillRemaining,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Receive all outstanding")
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(draft.lines) { index, line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.line.productName
                                    ?: "Product ${line.line.productId ?: "?"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.brandColors.textHeading,
                            )
                            Text(
                                text = line.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (line.line.isFullyReceived) {
                                    StatusSuccess
                                } else {
                                    MaterialTheme.brandColors.textSecondary
                                },
                            )
                        }
                        // Deliveries are when supplier prices move, so the retail
                        // price can be corrected right here, line by line.
                        line.line.productId?.let { productId ->
                            IconButton(
                                onClick = {
                                    onUpdatePrice(
                                        ReferenceOption(
                                            id = productId,
                                            title = line.line.productName ?: "Product #$productId",
                                        ),
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sell,
                                    contentDescription = "Update price",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        FormField(
                            value = line.entered,
                            onValueChange = { onQuantityChange(index, it) },
                            label = "Now",
                            isError = line.exceedsRemaining,
                            // A line with nothing outstanding stays visible so the
                            // operator can see it exists, but cannot be added to.
                            enabled = !line.line.isFullyReceived,
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                            modifier = Modifier.width(110.dp),
                        )
                    }
                }
            }

            if (draft.anyExceedsRemaining) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A line is over its outstanding quantity. Reduce it to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Receiving now: ${UomMath.pretty(draft.enteredTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.brandColors.textHeading,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onConfirm,
                enabled = draft.canSubmit,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Confirm goods in", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.brandColors.textSecondary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
