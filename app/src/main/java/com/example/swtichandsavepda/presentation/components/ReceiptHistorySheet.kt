package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.PurchaseOrderReceipt
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.presentation.PrintJobKeys
import com.example.swtichandsavepda.presentation.PrintUiState
import com.example.swtichandsavepda.presentation.ReceiptHistory
import com.example.swtichandsavepda.ui.theme.StatusDanger
import com.example.swtichandsavepda.ui.theme.brandColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RECEIPT_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

/**
 * Every delivery booked against one purchase order.
 *
 * This is the audit trail a partially-received PO needs: three deliveries of 2,
 * 3 and 5 against an order for 10 are indistinguishable from one delivery of 10
 * unless each is recorded separately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptHistorySheet(
    history: ReceiptHistory,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    printState: PrintUiState,
    onPrintReceipt: (PurchaseOrderReceipt) -> Unit,
    onDismissPrintMessage: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Receiving history",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.brandColors.textHeading,
            )
            Text(
                text = history.orderReference,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textSecondary,
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (printState.isPrinting || printState.message != null) {
                PrintFeedback(state = printState, onDismiss = onDismissPrintMessage)
                Spacer(modifier = Modifier.height(10.dp))
            }

            when {
                history.isLoading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                history.error != null -> Column {
                    Text(
                        text = history.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }

                history.receipts.isEmpty() -> Text(
                    text = "Nothing has been booked in against this order yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.brandColors.textSecondary,
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(history.receipts, key = { it.id }) { receipt ->
                        ReceiptRow(
                            receipt = receipt,
                            isPrinting = printState.isPrinting(PrintJobKeys.goodsReceived(receipt)),
                            canPrint = !printState.isPrinting,
                            onPrint = { onPrintReceipt(receipt) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                Text("Close", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    receipt: PurchaseOrderReceipt,
    isPrinting: Boolean,
    canPrint: Boolean,
    onPrint: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receipt.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.brandColors.textHeading,
                )
                Text(
                    text = buildString {
                        append(UomMath.pretty(receipt.totalReceived))
                        append(" received")
                        receipt.receivedAtEpochMs?.let {
                            append(" · ")
                            append(RECEIPT_TIME.format(Instant.ofEpochMilli(it)))
                        }
                        receipt.receivedBy?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.brandColors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // A GRN runs the same pending → confirmed/rejected lifecycle as any
            // other document, and a rejected one is exactly what an operator
            // chasing a discrepancy needs to see.
            PortalStateChip(state = receipt.portalState)
        }

        receipt.rejectReason?.takeIf { it.isNotBlank() }?.let { reason ->
            Text(
                text = "Rejected: $reason",
                style = MaterialTheme.typography.bodySmall,
                color = StatusDanger,
            )
        }

        receipt.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textTertiary,
            )
        }

        receipt.lines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            ) {
                Text(
                    text = line.productName ?: "Product ${line.productId ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.brandColors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = line.quantityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.brandColors.textHeading,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PrintButton(isPrinting = isPrinting, enabled = canPrint, onClick = onPrint, label = "Print GRN")
        }
    }
}
